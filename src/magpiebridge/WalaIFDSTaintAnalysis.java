package magpiebridge;

import com.ibm.wala.cast.ipa.callgraph.AstSSAPropagationCallGraphBuilder;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.cast.util.SourceBuffer;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.dataflow.IFDS.ICFGSupergraph;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.dataflow.IFDS.PartiallyBalancedTabulationSolver;
import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.intset.MutableMapping;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import magpiebridge.core.AnalysisConsumer;
import magpiebridge.core.AnalysisResult;
import magpiebridge.core.Kind;
import org.eclipse.lsp4j.DiagnosticSeverity;

public abstract class WalaIFDSTaintAnalysis
    extends WalaTaintAnalysis<Function<BasicBlockInContext<IExplodedBasicBlock>, Boolean>> {
  protected CallGraph callGraph;
  protected AstSSAPropagationCallGraphBuilder builder;

  public WalaIFDSTaintAnalysis(MutableMapping<String> uriCache) {
    super(uriCache);
  }

  @Override
  public void analyze(Collection<? extends Module> files, AnalysisConsumer server, boolean rerun) {
    try {
      builder = makeBuilder(files, server);
      callGraph = builder.makeCallGraph(builder.getOptions());

      ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> supergraph =
          ICFGSupergraph.make(callGraph);

      ZhinuDomain domain = new ZhinuDomain();

      /** perform the tabulation analysis and return the {@link TabulationResult} */
      PartiallyBalancedTabulationSolver<
              BasicBlockInContext<IExplodedBasicBlock>,
              CGNode,
              Pair<Integer, List<CAstSourcePositionMap.Position>>>
          solver =
              PartiallyBalancedTabulationSolver.createPartiallyBalancedTabulationSolver(
                  new ZhinuProblem(supergraph, domain, this, callGraph), null);
      TabulationResult<
              BasicBlockInContext<IExplodedBasicBlock>,
              CGNode,
              Pair<Integer, List<CAstSourcePositionMap.Position>>>
          R = solver.solve();

      Set<List<CAstSourcePositionMap.Position>> result = HashSetFactory.make();

      System.err.println(R);

      R.getSupergraphNodesReached()
          .forEach(
              (sbb) -> {
                if (sinkFinder().apply(sbb)) {
                  SSAInstruction inst = sbb.getDelegate().getInstruction();
                  CAstSourcePositionMap.Position sink =
                      ((AstMethod) sbb.getMethod())
                          .debugInfo()
                          .getInstructionPosition(inst.iIndex());
                  System.err.println("sink " + inst);
                  R.getResult(sbb)
                      .foreach(
                          i -> {
                            Pair<Integer, List<CAstSourcePositionMap.Position>> trace =
                                domain.getMappedObject(i);
                            for (int j = 0; j < inst.getNumberOfUses(); j++) {
                              if (inst.getUse(j) == trace.fst) {
                                List<CAstSourcePositionMap.Position> x =
                                    new LinkedList<>(trace.snd);
                                x.add(sink);
                                result.add(x);
                              }
                            }
                          });
                }
              });

      Set<AnalysisResult> ars = HashSetFactory.make();
      result.forEach(
          lp -> {
            lp.forEach(
                step -> {
                  ars.add(
                      new AnalysisResult() {

                        @Override
                        public Kind kind() {
                          return Kind.Diagnostic;
                        }

                        @Override
                        public String toString(boolean useMarkdown) {
                          return "tainted flow step";
                        }

                        @Override
                        public CAstSourcePositionMap.Position position() {
                          return Print.fixIncludedURL(step);
                        }

                        @Override
                        public Iterable<Pair<CAstSourcePositionMap.Position, String>> related() {
                          Set<Pair<CAstSourcePositionMap.Position, String>> trace =
                              HashSetFactory.make();
                          lp.forEach(
                              e -> {
                                trace.add(Pair.make(e, "flow step"));
                              });
                          return trace;
                        }

                        @Override
                        public DiagnosticSeverity severity() {
                          return DiagnosticSeverity.Warning;
                        }

                        @Override
                        public Pair<CAstSourcePositionMap.Position, String> repair() {
                          // TODO Auto-generated method stub
                          return null;
                        }

                        @Override
                        public String code() {
                          try {
                            return new SourceBuffer(position()).toString();
                          } catch (IOException e) {
                            return "unknown";
                          }
                        }
                      });
                });
          });
      server.consume(ars, source());
    } catch (WalaException | IllegalArgumentException | CancelException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
}
