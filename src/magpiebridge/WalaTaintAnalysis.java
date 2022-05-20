package magpiebridge;

import com.ibm.wala.cast.ipa.callgraph.AstSSAPropagationCallGraphBuilder;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.intset.MutableMapping;
import java.util.Collection;
import magpiebridge.core.AnalysisConsumer;
import magpiebridge.core.ServerAnalysis;

public abstract class WalaTaintAnalysis<T> implements ServerAnalysis {

  protected abstract T sourceFinder();

  protected abstract T sinkFinder();

  protected final MutableMapping<String> uriCache;

  public WalaTaintAnalysis(MutableMapping<String> uriCache) {
    this.uriCache = uriCache;
  }

  public abstract AstSSAPropagationCallGraphBuilder makeBuilder(
      Collection<? extends Module> files, AnalysisConsumer server) throws WalaException;
}
