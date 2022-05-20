package magpiebridge;

import com.ibm.wala.util.intset.MutableMapping;
import java.util.function.Supplier;
import magpiebridge.core.MagpieServer;
import magpiebridge.core.ServerAnalysis;
import magpiebridge.core.ServerConfiguration;
import magpiebridge.core.ToolAnalysis;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class Main {
  public static void main(String... args) {
    boolean socket = true;
    Supplier<MagpieServer> createServer =
        () -> {
          ServerConfiguration config = new ServerConfiguration();
          config.setDoAnalysisBySave(false);
          config.setDoAnalysisByOpen(true);
          MagpieServer server = new MagpieServer(config);
          String language = "javascript";
          MutableMapping<String> uriCache = MutableMapping.make();
          ServerAnalysis analysis = new JSWalaIFDSTaintAnalysis(uriCache);
          Either<ServerAnalysis, ToolAnalysis> either = Either.forLeft(analysis);
          server.addAnalysis(either, language);
          return server;
        };
    if (socket) {
      int port = 5007;
      MagpieServer.launchOnSocketPort(port, createServer);
    } else {
      createServer.get().launchOnStdio();
    }
  }
}
