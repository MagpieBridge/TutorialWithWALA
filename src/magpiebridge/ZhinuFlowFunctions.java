package magpiebridge;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.cast.types.AstMethodReference;
import com.ibm.wala.cfg.Util;
import com.ibm.wala.dataflow.IFDS.IPartiallyBalancedFlowFunctions;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.dataflow.IFDS.IdentityFlowFunction;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSACFG.BasicBlock;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSAUnaryOpInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.util.intset.MutableIntSet;

class ZhinuFlowFunctions
    implements IPartiallyBalancedFlowFunctions<BasicBlockInContext<IExplodedBasicBlock>> {
  private final ZhinuDomain domain;
  private final ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> supergraph;
  private final CallGraph callGraph;

  public ZhinuFlowFunctions(
      ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> supergraph,
      ZhinuDomain domain,
      CallGraph cg) {
    this.supergraph = supergraph;
    this.domain = domain;
    this.callGraph = cg;
  }

  @Override
  public IUnaryFlowFunction getNormalFlowFunction(
      BasicBlockInContext<IExplodedBasicBlock> src, BasicBlockInContext<IExplodedBasicBlock> dest) {
    final IExplodedBasicBlock ebb = src.getDelegate();
    final IExplodedBasicBlock dbb = dest.getDelegate();

    return new IUnaryFlowFunction() {

      private boolean isSanitizerTest(SSAInstruction inst) {
        if (inst instanceof SSAConditionalBranchInstruction) {
          DefUse du = src.getNode().getDU();
          if (du.getDef(inst.getUse(0)) instanceof SSAAbstractInvokeInstruction) {
            SSAAbstractInvokeInstruction sanitize =
                (SSAAbstractInvokeInstruction) du.getDef(inst.getUse(0));
            MethodReference callee = sanitize.getDeclaredTarget();
            if (callee.getSelector().equals(AstMethodReference.fnSelector)) {
              Set<CGNode> callees = callGraph.getPossibleTargets(src.getNode(), sanitize.getCallSite());
              for(CGNode calleeNode : callees) {
            	  if (calleeNode.getMethod().getDeclaringClass().getName().toString().contains("isSafe")) {
            		  return true;
            	  }
              }
            } else {
            	return callee.getName().toString().contains("isSafe");
            }
          }
        }

        return false;
      }

      private void propagate(
          SSAInstruction inst,
          Pair<Integer, List<CAstSourcePositionMap.Position>> vn,
          MutableIntSet r) {
        boolean propagates =
            inst instanceof SSAPhiInstruction
                || inst instanceof SSAPiInstruction
                || inst instanceof SSABinaryOpInstruction
                || inst instanceof SSAUnaryOpInstruction;

        if (propagates) {
          for (int i = 0; i < inst.getNumberOfUses(); i++) {
            if (vn.fst == inst.getUse(i)) {
              List<CAstSourcePositionMap.Position> x = new LinkedList<>(vn.snd);
              if (src.getMethod() instanceof AstMethod && inst.iIndex() >= 0) {
                CAstSourcePositionMap.Position next =
                    ((AstMethod) src.getMethod()).debugInfo().getInstructionPosition(inst.iIndex());
                if (next != null) {
                  x.add(next);
                }
              }
              Pair<Integer, List<CAstSourcePositionMap.Position>> nvn = Pair.make(inst.getDef(), x);
              if (!domain.hasMappedIndex(nvn)) {
                domain.add(nvn);
              }
              r.add(domain.getMappedIndex(nvn));
            }
          }
        }
      }

      @Override
      public IntSet getTargets(int d1) {
        Pair<Integer, List<CAstSourcePositionMap.Position>> vn = domain.getMappedObject(d1);
        int incoming = domain.getMappedIndex(vn);
        MutableIntSet r = IntSetUtil.make(new int[] {incoming});
        dbb.iteratePhis()
            .forEachRemaining(
                (inst) -> {
                  propagate(inst, vn, r);
                });
        propagate(ebb.getInstruction(), vn, r);

        if (incoming != 0 && isSanitizerTest(ebb.getInstruction())) {
          SSACFG cfg = src.getNode().getIR().getControlFlowGraph();
          BasicBlock from = cfg.getNode(ebb.getOriginalNumber());
          BasicBlock to = cfg.getNode(dbb.getOriginalNumber());
          if (!Util.getTakenSuccessor(cfg, from).equals(to)) {
            r.remove(incoming);
          }
        }

        return r;
      }
    };
  }

  // taint parameters from arguments
  @Override
  public IUnaryFlowFunction getCallFlowFunction(
      BasicBlockInContext<IExplodedBasicBlock> src,
      BasicBlockInContext<IExplodedBasicBlock> dest,
      BasicBlockInContext<IExplodedBasicBlock> ret) {
    final IExplodedBasicBlock ebb = src.getDelegate();
    SSAInstruction inst = ebb.getInstruction();
    return (d1) -> {
      MutableIntSet r = IntSetUtil.make();
      Pair<Integer, List<CAstSourcePositionMap.Position>> vn = domain.getMappedObject(d1);
      for (int i = 0; i < inst.getNumberOfUses(); i++) {
        if (vn.fst == inst.getUse(i)) {
          List<CAstSourcePositionMap.Position> x = new LinkedList<>(vn.snd);
          if (src.getMethod() instanceof AstMethod) {
            CAstSourcePositionMap.Position next =
                ((AstMethod) src.getMethod()).debugInfo().getInstructionPosition(inst.iIndex());
            if (next != null) {
              x.add(next);
            }
          }
          Pair<Integer, List<CAstSourcePositionMap.Position>> key = Pair.make(i + 1, x);
          if (!domain.hasMappedIndex(key)) {
            domain.add(key);
          }
          r.add(domain.getMappedIndex(key));
        }
      }
      return r;
    };
  }

  // pass tainted values back to caller, if appropriate
  @Override
  public IUnaryFlowFunction getReturnFlowFunction(
      BasicBlockInContext<IExplodedBasicBlock> call,
      BasicBlockInContext<IExplodedBasicBlock> src,
      BasicBlockInContext<IExplodedBasicBlock> dest) {
    int retVal = call.getLastInstruction().getDef();
    return (d1) -> {
      Pair<Integer, List<CAstSourcePositionMap.Position>> vn = domain.getMappedObject(d1);
      MutableIntSet result = IntSetUtil.make();
      supergraph
          .getPredNodes(src)
          .forEachRemaining(
              pb -> {
                SSAInstruction inst = pb.getDelegate().getInstruction();
                if (inst instanceof SSAReturnInstruction && inst.getUse(0) == vn.fst) {
                  List<CAstSourcePositionMap.Position> x = new LinkedList<>(vn.snd);
                  if (src.getMethod() instanceof AstMethod) {
                    CAstSourcePositionMap.Position next =
                        ((AstMethod) src.getMethod())
                            .debugInfo()
                            .getInstructionPosition(inst.iIndex());
                    if (next != null) {
                      x.add(next);
                    }
                  }
                  Pair<Integer, List<CAstSourcePositionMap.Position>> key = Pair.make(retVal, x);
                  if (!domain.hasMappedIndex(key)) {
                    domain.add(key);
                  }
                  result.add(domain.getMappedIndex(key));
                }
              });
      return result;
    };
  }

  // local variables are not changed by calls.
  @Override
  public IUnaryFlowFunction getCallToReturnFlowFunction(
      BasicBlockInContext<IExplodedBasicBlock> src, BasicBlockInContext<IExplodedBasicBlock> dest) {
    return IdentityFlowFunction.identity();
  }

  // missing a callee, so assume it does nothing and keep local information.
  @Override
  public IUnaryFlowFunction getCallNoneToReturnFlowFunction(
      BasicBlockInContext<IExplodedBasicBlock> src, BasicBlockInContext<IExplodedBasicBlock> dest) {
    return IdentityFlowFunction.identity();
  }

  // data flow back from an unknown call site, so flow to anything possible.
  @Override
  public IUnaryFlowFunction getUnbalancedReturnFlowFunction(
      BasicBlockInContext<IExplodedBasicBlock> src, BasicBlockInContext<IExplodedBasicBlock> dest) {
    CGNode retFromNode = src.getNode();
    CGNode returnToNode = dest.getNode();
    return d1 -> {
      Pair<Integer, List<CAstSourcePositionMap.Position>> vn = domain.getMappedObject(d1);
      MutableIntSet result = IntSetUtil.make();
      callGraph
          .getPossibleSites(returnToNode, retFromNode)
          .forEachRemaining(
              site -> {
                for (SSAAbstractInvokeInstruction call : returnToNode.getIR().getCalls(site)) {
                  supergraph
                      .getPredNodes(src)
                      .forEachRemaining(
                          pb -> {
                            SSAInstruction ret = pb.getDelegate().getInstruction();
                            if (ret instanceof SSAReturnInstruction && ret.getUse(0) == vn.fst) {
                              List<CAstSourcePositionMap.Position> x = new LinkedList<>(vn.snd);
                              if (src.getMethod() instanceof AstMethod) {
                                CAstSourcePositionMap.Position next =
                                    ((AstMethod) src.getMethod())
                                        .debugInfo()
                                        .getInstructionPosition(ret.iIndex());
                                if (next != null) {
                                  x.add(next);
                                }
                              }
                              Pair<Integer, List<CAstSourcePositionMap.Position>> key =
                                  Pair.make(call.getDef(), x);
                              if (!domain.hasMappedIndex(key)) {
                                domain.add(key);
                              }
                              result.add(domain.getMappedIndex(key));
                            }
                          });
                }
              });
      return result;
    };
  }
}
