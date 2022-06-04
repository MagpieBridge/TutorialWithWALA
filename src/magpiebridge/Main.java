package magpiebridge;

import com.ibm.wala.classLoader.Module;
import com.ibm.wala.classLoader.SourceFileModule;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.intset.MutableMapping;
import java.io.File;
import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;
import magpiebridge.core.AnalysisConsumer;
import magpiebridge.core.AnalysisResult;
import magpiebridge.core.MagpieServer;
import magpiebridge.core.ServerAnalysis;
import magpiebridge.core.ServerConfiguration;
import magpiebridge.core.ToolAnalysis;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class Main {
  public static void main(String... args) {
    boolean socket = true;
    MutableMapping<String> uriCache = MutableMapping.make();
    ServerAnalysis analysis = new JSWalaIFDSTaintAnalysis(uriCache);
    Supplier<MagpieServer> createServer =
        () -> {
          ServerConfiguration config = new ServerConfiguration();
          config.setDoAnalysisBySave(false);
          config.setDoAnalysisByOpen(true);
          MagpieServer server = new MagpieServer(config);
          String language = "javascript";
          Either<ServerAnalysis, ToolAnalysis> either = Either.forLeft(analysis);
          server.addAnalysis(either, language);
          return server;
        };
    if (args.length > 0) {
      AnalysisConsumer ac =
          new AnalysisConsumer() {
            @Override
            public void consume(Collection<AnalysisResult> results, String source) {
              results.forEach(ar -> System.err.println(ar.toString(false) + " " + ar.position()));
            }
          };

      Set<Module> modules = HashSetFactory.make();
      for (String arg : args) {
        File f = new File(arg);
        modules.add(new SourceFileModule(f, f.getName(), null));
      }

      analysis.analyze(modules, ac, false);
    } else if (socket) {
      int port = 5007;
      MagpieServer.launchOnSocketPort(port, createServer);
    } else {
      createServer.get().launchOnStdio();
    }
  }
}
