package magpiebridge;

import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.dataflow.IFDS.IMergeFunction;
import com.ibm.wala.dataflow.IFDS.IPartiallyBalancedFlowFunctions;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.dataflow.IFDS.PartiallyBalancedTabulationProblem;
import com.ibm.wala.dataflow.IFDS.PathEdge;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Pair;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class ZhinuProblem
    implements PartiallyBalancedTabulationProblem<
        BasicBlockInContext<IExplodedBasicBlock>,
        CGNode,
        Pair<Integer, List<CAstSourcePositionMap.Position>>> {

  private ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> supergraph;
  private ZhinuDomain domain;
  private WalaTaintAnalysis<Function<BasicBlockInContext<IExplodedBasicBlock>, Boolean>> analysis;
  private final ZhinuFlowFunctions flowFunctions;

  /** path edges corresponding to taint sources */
  private final Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> initialSeeds =
      collectInitialSeeds();

  public ZhinuProblem(
      ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> supergraph,
      ZhinuDomain domain,
      WalaTaintAnalysis<Function<BasicBlockInContext<IExplodedBasicBlock>, Boolean>> analysis,
      CallGraph callGraph) {
    this.supergraph = supergraph;
    this.domain = domain;
    this.analysis = analysis;
    flowFunctions = new ZhinuFlowFunctions(supergraph, domain, callGraph);
  }

  /**
   * we use the entry block of the CGNode as the fake entry when propagating from callee to caller
   * with unbalanced parens
   */
  @Override
  public BasicBlockInContext<IExplodedBasicBlock> getFakeEntry(
      BasicBlockInContext<IExplodedBasicBlock> node) {
    final CGNode cgNode = node.getNode();
    return getFakeEntry(cgNode);
  }

  /**
   * we use the entry block of the CGNode as the "fake" entry when propagating from callee to caller
   * with unbalanced parens
   */
  private BasicBlockInContext<IExplodedBasicBlock> getFakeEntry(final CGNode cgNode) {
    BasicBlockInContext<IExplodedBasicBlock>[] entriesForProcedure =
        supergraph.getEntriesForProcedure(cgNode);
    assert entriesForProcedure.length == 1;
    return entriesForProcedure[0];
  }

  /** collect sources of taint */
  private Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> collectInitialSeeds() {
    Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> result = HashSetFactory.make();
    for (BasicBlockInContext<IExplodedBasicBlock> bb : supergraph) {
      IExplodedBasicBlock ebb = bb.getDelegate();
      SSAInstruction instruction = ebb.getInstruction();
      if (analysis.sourceFinder().apply(bb)) {
        CAstSourcePositionMap.Position next =
            ((AstMethod) bb.getMethod()).debugInfo().getInstructionPosition(instruction.iIndex());
        Pair<Integer, List<CAstSourcePositionMap.Position>> fact =
            Pair.make(instruction.getDef(), Collections.singletonList(next));
        final CGNode cgNode = bb.getNode();
        int factNum = domain.add(fact);
        BasicBlockInContext<IExplodedBasicBlock> fakeEntry = getFakeEntry(cgNode);
        // note that the fact number used for the source of this path edge doesn't
        // really matter
        result.add(PathEdge.createPathEdge(fakeEntry, factNum, bb, factNum));
      }
    }
    return result;
  }

  @Override
  public IPartiallyBalancedFlowFunctions<BasicBlockInContext<IExplodedBasicBlock>>
      getFunctionMap() {
    return flowFunctions;
  }

  @Override
  public ZhinuDomain getDomain() {
    return domain;
  }

  /** we don't need a merge function; the default unioning of tabulation works fine */
  @Override
  public IMergeFunction getMergeFunction() {
    return null;
  }

  @Override
  public ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> getSupergraph() {
    return supergraph;
  }

  @Override
  public Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> initialSeeds() {
    return initialSeeds;
  }
}
