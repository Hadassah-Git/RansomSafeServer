import java.io.BufferedReader;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import javax.xml.bind.Marshaller.Listener;


//Communication between the virtual machine server and the proxy
public class ServerToProxy{

	ServerSocket serverSocket;
	Socket sockProxy;
	String host,vmIp;
	int port,vmPort;


	public static void main(String[] args) {
		// TODO Auto-generated method stub
		try {
			new ServerToProxy("127.0.0.1",8086);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public ServerToProxy(String host, int port) throws IOException {
		super();
		this.host = host;
		this.port = port;
		Communication();
	}
	
	public void Communication() {
		
		try {
			serverSocket=new ServerSocket(port);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		while(true)
		{
		try {
			sockProxy= serverSocket.accept();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	
		Thread thread = new Thread(new RequestHandlerVM(vmIp, sockProxy));
		thread.start();
		}
	}
	
	
	
}

