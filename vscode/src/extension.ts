'use strict';
import * as net from 'net';
import { workspace, ExtensionContext, window } from 'vscode';
import { LanguageClient, LanguageClientOptions, ServerOptions, StreamInfo } from 'vscode-languageclient';

var client: LanguageClient = null;

async function configureAndStartClient(context: ExtensionContext) {

	// Startup options for the language server
	const settings = workspace.getConfiguration("ZhinuIDE");
	const lspTransport: string = settings.get("lspTransport");
	let script = 'java';
	let relativePath = "TutorialWithWALA-1.0-SNAPSHOT.jar";
	let args = ['-jar', context.asAbsolutePath(relativePath)];
	const serverOptionsStdio = {
		run: { command: script, args: args },
		debug: { command: script, args: args }
	}

	const serverOptionsSocket = () => {
		const socket = net.connect({ port: 5007 })
		const result: StreamInfo = {
			writer: socket,
			reader: socket
		}
		return new Promise<StreamInfo>((resolve) => {
			socket.on("connect", () => resolve(result))
			socket.on("error", _ => {

				window.showErrorMessage(
					"Failed to connect to ZhinuIDE language server. Make sure that the language server is running " +
					"-or- configure the extension to connect via standard IO.")
				client = null;
			});
		})
	}

	const serverOptions: ServerOptions =
		(lspTransport === "stdio") ? serverOptionsStdio : (lspTransport === "socket") ? serverOptionsSocket : null

	let clientOptions: LanguageClientOptions = {
		documentSelector: [{ scheme: 'file', language: 'java' }],
		synchronize: {
			configurationSection: 'java',
			fileEvents: [workspace.createFileSystemWatcher('**/*.java')]
		}
	};

	// Create the language client and start the client.
	client = new LanguageClient('ZhinuIDE', 'ZhinuIDE', serverOptions, clientOptions);
	client.start();


	await client.onReady();
}

export async function activate(context: ExtensionContext) {
	configureAndStartClient(context);
	workspace.onDidChangeConfiguration(e => {
		if (client)
			client.stop().then(() => configureAndStartClient(context));
		else
			configureAndStartClient(context)
	})
}




