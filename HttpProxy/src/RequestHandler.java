

import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Date;

import javax.imageio.ImageIO;
public class RequestHandler implements Runnable {

	/**
	 * Socket connected to client passed by Proxy server
	 */
	private Socket proxyToClientSocket;
	
	 
	

	/**
	 * Read data client sends to proxy
	 */
		private InputStream proxyToClientIn;

	/**
	 * Send data from proxy to client
	 */
		private OutputStream proxyToClientOut;
	
	/**
	 * Send data from proxy to Virtual Machine
	 */
	BufferedWriter proxyToVirtualMachine;
	

	InetAddress host;

	/**
	 * Thread that is used to transmit data read from client to server when using HTTPS
	 * Reference to this is required so it can be closed once completed.
	 */
	private Thread httpsClientToServer;

	/**
	 * the last modify of web page
	 */
	long lastModified=0;

	/**
	 * Creates a ReuqestHandler object capable of servicing HTTP(S) GET requests
	 * @param clientSocket socket connected to the client
	 */
	public RequestHandler(Socket socket) {
		//super();
		this.proxyToClientSocket = socket;
		try {
			proxyToClientIn = proxyToClientSocket.getInputStream();
			proxyToClientOut = proxyToClientSocket.getOutputStream();
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	
	
	/**
	 * Reads and examines the requestString and calls the appropriate method based 
	 * on the request type. 
	 */
	@Override
	public void run() {
		
		

		// Get Request from client
				String requestString="";
				byte[] buffer = new byte[2048];
				int read=0;
				try{
					 do
			            {
						    read = proxyToClientIn.read(buffer, 0, buffer.length);
						    if (read>=0)
						    {
						    	String s=new String (buffer,0,buffer.length);
						    	requestString += s;
						    }

			           } while (read == buffer.length);
			           // }while (read!=0 && read!=-1 || read == buffer.length);
					//requestString = proxyToClientIn.readUTF();
				} catch (IOException e) {
					e.printStackTrace();
					System.out.println("Error reading request from client");
					return;
				}
		if (requestString=="") 
			return;
		System.out.println("Request Received " + requestString);

		// Parse out URL

		String reqSplit[] = requestString.split(" ",3);
		String method=reqSplit[0];
		String remoteUri=reqSplit[1];
		

		/*// Prepend http:// if necessary to create correct URL

		if(!remoteUri.substring(0,4).equals("http")){//�� ������ ���� ��� �HTTP ����� �� �� 
			String temp = "http://";
			remoteUri = temp + remoteUri;
		}*/


		// Check if URL is in the blockedSites
		if(Proxy.isBlocked(remoteUri)){
			System.out.println("Blocked site requested : " + remoteUri);
			blockedSiteRequested();
		
			return;
		}
		
		sendEror();
		
		URI uri = null;
		// Check request type
		if(method.equals("CONNECT")){
			
			try {
				uri = new URI("https://" + remoteUri);
			
			System.out.println("HTTPS Request for : " + uri +":" + 443 +"\n");
		
				handleHTTPSRequest(uri,443);
			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		} 

		else{
			//http
			// Check if we have a cached copy
			
			URL url;
			CacheValue cacheValue;

			try {
				uri = new URI(remoteUri);
			} catch (URISyntaxException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			if((cacheValue = Proxy.getCachedPage(remoteUri)) != null){
				System.out.println("Cached Copy found for : " + remoteUri + "\n");
				//get the last modify
				try {
					url = new URL(remoteUri);
					HttpURLConnection httpConnection = (HttpURLConnection) url.openConnection();
						httpConnection.setRequestMethod("HEAD");
						httpConnection.connect();
						lastModified = httpConnection.getLastModified();
						if (lastModified != 0) {
						  System.out.println(new Date(lastModified));
						} else {
						  System.out.println("Last-Modified not returned");
					//	  Date lastModified=new Date();
						}
						httpConnection.disconnect();
				} catch (MalformedURLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				//Check if the page is different
				if(checkPageChange(lastModified))
				{
					sendCachedPageToClient(cacheValue.getFile());
				}
				else
				{
					System.out.println("HTTP GET modified for : " + remoteUri + "\n");
					if( sendToVM(requestString))
						sendNonCachedToClient(remoteUri,uri,requestString);
				}
			}
				else {
				System.out.println("HTTP GET for : " + remoteUri + "\n");
				if( sendToVM(requestString))
					sendNonCachedToClient(remoteUri,uri,requestString);
				else {
					sendEror();
				}
			}
		}
	} 



	private void sendEror(){
			// Read from File containing cached web page
		CacheValue cacheValue = Proxy.getCachedPage("ERROR");
		File valueFile=cacheValue.getFile();
		
				try{
				// If file is an image write data to client using buffered image.
				String fileExtension = valueFile.getName().substring(valueFile.getName().lastIndexOf('.'));
				
				// Response that will be sent to the server
				String response;
				
					// Read in image from storage
					BufferedImage image = ImageIO.read(valueFile);
					
					if(image != null ){
						System.out.println("Image " + valueFile.getName() + " was null");
						response = "HTTP/1.0 404 NOT FOUND \r\n" +
								"Proxy-agent: ProxyServer/1.0\r\n" +
								"\r\n";
						proxyToClientOut.write(response.getBytes());
						proxyToClientOut.flush();
					} else {
						//String line = "HTTP/1.0 200 Connection established\r\n" +
							//	"Proxy-Agent: ProxyServer/1.0\r\n" +
								//"\r\n";
						//proxyToClientOut.write(line.getBytes());
						response = "HTTP/1.0 200 OK\r\n" +
								"Proxy-agent: ProxyServer/1.0\r\n" +
								"\r\n";
						proxyToClientOut.write(response.getBytes());
						
					//	System.out.println(fileExtension);
						ImageIO.write(image, "png", proxyToClientOut);
						
						proxyToClientOut.flush();
					
				} 

			} catch (IOException e) {
				System.out.println("Error Sending Cached file to client");
				e.printStackTrace();
			}
		}
		
	




	//check if the page change via the lastmodify
	private Boolean checkPageChange(long modifyFromCatche) {
		
		if ((lastModified<=modifyFromCatche)) 
		{
			return true;
		}
		return false;
	}


 private boolean sendToVM(String req)
{
	try {
		Socket VMSock=new Socket("127.0.0.1",8086);
		DataOutputStream VMOut=new DataOutputStream(VMSock.getOutputStream());
		VMOut.writeUTF(req);
		DataInputStream VMIn=new DataInputStream(VMSock.getInputStream());
		int checkFlag=VMIn.readInt();
		if (checkFlag==1)
			//Ransom page
			return false;
		else if (checkFlag==2)
			// Page back to safe shore
			return false;
		
			
	} catch (UnknownHostException e1) {
		// TODO Auto-generated catch block
		e1.printStackTrace();
	} catch (IOException e1) {
		// TODO Auto-generated catch block
		e1.printStackTrace();
	}
	finally{
		return true;
	}
}

	/**
		 * Sends the contents of the file specified by the urlString to the client
		 * @param urlString URL ofthe file requested
		 */
		private void sendNonCachedToClient(String urlString,URI uri,String req){
	boolean img_flag=false;			
	//send the request to the vm manage server to scan it from "kofer" virus

			
			try{
				
				// Compute a logical file name as per schema
				// This allows the files on stored on disk to resemble that of the URL it was taken from
				int fileExtensionIndex = urlString.lastIndexOf(".");
				String fileExtension;
	
				// Get the type of file
				fileExtension = urlString.substring(fileExtensionIndex, urlString.length());
	
				// Get the initial file name
				String fileName = urlString.substring(0,fileExtensionIndex);
	
	
				// Trim off http://www. as no need for it in file name
				fileName = fileName.substring(fileName.indexOf('.')+1);
	
				// Remove any illegal characters from file name
				fileName = fileName.replace("/", "__");
				fileName = fileName.replace('.','_');
				
				// Trailing / result in index.html of that directory being fetched
				if(fileExtension.contains("/")){
					fileExtension = fileExtension.replace("/", "__");
					fileExtension = fileExtension.replace('.','_');
					fileExtension += ".html";
				}
			
				fileName = fileName + fileExtension;
	
				// Attempt to create File to cache to
				boolean caching = true;
				File fileToCache = null;
				BufferedWriter fileToCacheBW = null;
	
				try{
					// Create File to cache 
					fileToCache = new File("cached/" + fileName);
	
					if(!fileToCache.exists()){
						fileToCache.createNewFile();
					}
	
					// Create Buffered output stream to write to cached copy of file
					fileToCacheBW = new BufferedWriter(new FileWriter(fileToCache));
				}
				catch (IOException e){
					System.out.println("Couldn't cache: " + fileName);
					caching = false;
					e.printStackTrace();
				} catch (NullPointerException e) {
					System.out.println("NPE opening file");
				}
	
	
				
				// Open a socket to the remote server 
				Socket proxyToServerSocket = new Socket(uri.getHost(), 80);
			//	proxyToServerSocket.setSoTimeout(5000);
				InputStream inServer= proxyToServerSocket.getInputStream();
				OutputStream outServer=proxyToServerSocket.getOutputStream();
				InputStream inClient= proxyToClientSocket.getInputStream();
				OutputStream outClient=proxyToClientSocket.getOutputStream();
	
				// Check if file is an image
				if((fileExtension.contains(".png")) || fileExtension.contains(".jpg") ||
						fileExtension.contains(".jpeg") || fileExtension.contains(".gif")){
				/*	 final FileOutputStream fileOutputStream = new FileOutputStream(fileToCache);
					    

					    // Header end flag.
					    boolean headerEnded = false;

					    byte[] bytes = new byte[2048];
					    int length;
					    while ((length = inServer.read(bytes)) != -1) {
					        // If the end of the header had already been reached, write the bytes to the file as normal.
					        if (headerEnded)
					            fileOutputStream.write(bytes, 0, length);

					        // This locates the end of the header by comparing the current byte as well as the next 3 bytes
					        // with the HTTP header end "\r\n\r\n" (which in integer representation would be 13 10 13 10).
					        // If the end of the header is reached, the flag is set to true and the remaining data in the
					        // currently buffered byte array is written into the file.
					        else {
					            for (int i = 0; i < 2045; i++) {
					                if (bytes[i] == 13 && bytes[i + 1] == 10 && bytes[i + 2] == 13 && bytes[i + 3] == 10) {
					                    headerEnded = true;
					                    fileOutputStream.write(bytes, i+4 , 2048-i-4);
					                    
					                    break;
					                }
					            }
					        }
					    }
					    inputStream.close();
					    fileOutputStream.clo*/
					/// Create the URL
					URL remoteURL = new URL(urlString);
					BufferedImage image = ImageIO.read(remoteURL);
	
					if(image != null) {
						// Cache the image to disk
						ImageIO.write(image, fileExtension.substring(1), fileToCache);
	
						// Send response code to client
						String line = "HTTP/1.0 200 OK\n" +
								"Proxy-agent: ProxyServer/1.0\n" +
								"\r\n";
						proxyToClientOut.write(line.getBytes());
						proxyToClientOut.flush();
	
						// Send them the image data
					// BufferedImage image = ImageIO.read(in);
						ImageIO.write(image, fileExtension.substring(1), proxyToClientSocket.getOutputStream());
	
					// No image received from remote server
					} else {
						System.out.println("Sending 404 to client as image wasn't received from server"
								+ fileName);
						String error = "HTTP/1.0 404 NOT FOUND\n" +
								"Proxy-agent: ProxyServer/1.0\n" +
								"\r\n";
						proxyToClientOut.write(error.getBytes());
						proxyToClientOut.flush();
						return;
					}
				} 
	
				
				// File is a text file
				else {
									
				/*	// Create the URL
					
					URL remoteURL = new URL(urlString);
					// Create a connection to remote server
					HttpURLConnection proxyToServerCon = (HttpURLConnection)remoteURL.openConnection();
					proxyToServerCon.setRequestProperty("Content-Type", 
							"application/x-www-form-urlencoded");
					proxyToServerCon.setRequestProperty("Content-Language", "en-US");  
					proxyToServerCon.setUseCaches(false);
					proxyToServerCon.setDoOutput(true);
				
					// Create Buffered Reader from remote Server
					BufferedReader proxyToServerBR = new BufferedReader(new InputStreamReader(proxyToServerCon.getInputStream()));
					
	
					// Send success code to client
					String line = "HTTP/1.0 200 OK\n" +
							"Proxy-agent: ProxyServer/1.0\n" +
							"\r\n";
					proxyToClientOut.write(line.getBytes());
				
					
					
					// Read from input stream between proxy and remote server
					while((line = proxyToServerBR.readLine()) != null){
						// Send on data to client
						proxyToClientOut.write(line.getBytes());
	
						// Write to our cached copy of the file
						if(caching){
							fileToCacheBW.write(line);
						}
					}
					
					// Ensure all data is sent by this point
					fileToCacheBW.flush();
	
					// Close Down Resources
					if(proxyToServerBR != null){
						proxyToServerBR.close();
					}
				}
	*/
					try{
						
						
					//	String line,data=req;
						
						// send the request to server
						/*do {
						    line=proxyToClientIn.readLine();
						    if (line!=null)
							data+=line;
						    data+="\r\n";
							
						}while(line!=null);
						data+="\r\n\r\n";*/
						System.out.println(req);
						
						outServer.write(req.getBytes());
						outServer.flush();
						
						// send the response to client	
						
						try {
							// Read byte by byte from client and send directly to server
							byte[] buffer = new byte[4096];
							int read=0;
							do {
								read = inServer.read(buffer,0,buffer.length);
								if (read >= 0) {
									
								
									outClient.write(buffer, 0, read);
								//	if (proxyToServerSocket.getInputStream().available() < 1) {
									if(caching){
										fileToCacheBW.write(buffer.toString());
									}
										outClient.flush();
										fileToCacheBW.flush();
								}
								
								
							} while (read != 0 && read !=-1);
								//&& proxyToServerSocket.getInputStream().available()>0);
							// Close Down Resources
							if(proxyToServerSocket.isConnected()){
								proxyToServerSocket.close();
							}
	
							if(proxyToClientSocket.isConnected()){
								proxyToClientSocket.close();
							}
						}
						catch (SocketTimeoutException ste) {
							// TODO: handle exception
						}
						catch (IOException e) {
							System.out.println("Proxy to client HTTP read timed out");
							e.printStackTrace();
						}
						finally
						{
						// Close Down Resources
						if(proxyToServerSocket.isConnected()){
							proxyToServerSocket.close();
						}
	
						if(proxyToClientSocket.isConnected()){
							proxyToClientSocket.close();
						}
						}
						
					} catch (SocketTimeoutException e) {
						String line = "HTTP/1.0 504 Timeout Occured after 10s\n" +
								"User-Agent: ProxyServer/1.0\n" +
								"\r\n";
						try{
							proxyToClientOut.write(line.getBytes());
							proxyToClientOut.flush();
						} catch (IOException ioe) {
							ioe.printStackTrace();
						}
					} 
					catch (Exception e){
						System.out.println("Error on HTTP : " + host );
						e.printStackTrace();
					}
				
				}
				if(caching){
					CacheValue valueToCache=new CacheValue(fileToCache, lastModified);
					// Ensure data written and add to our cached hash maps
					fileToCacheBW.flush();
					Proxy.addCachedPage(urlString, valueToCache);
				}
	
				// Close down resources
				if(fileToCacheBW != null){
					fileToCacheBW.close();
				}
	
				//if(proxyToClientOut != null){
				//	proxyToClientOut.close();
				//}
			} 
	
			catch (Exception e){
				e.printStackTrace();
			}
		}




	/**
	 * Sends the specified cached file to the client
	 * @param cachedFile The file to be sent (can be image/text)
	 */
	private void sendCachedPageToClient(File cachedFile){
		// Read from File containing cached web page
		try{
			// If file is an image write data to client using buffered image.
			String fileExtension = cachedFile.getName().substring(cachedFile.getName().lastIndexOf('.'));
			
			// Response that will be sent to the server
			String response;
			if((fileExtension.contains(".png")) || fileExtension.contains(".jpg") ||
					fileExtension.contains(".jpeg") || fileExtension.contains(".gif")){
				// Read in image from storage
				BufferedImage image = ImageIO.read(cachedFile);
				
				if(image == null ){
					System.out.println("Image " + cachedFile.getName() + " was null");
					response = "HTTP/1.0 404 NOT FOUND \n" +
							"Proxy-agent: ProxyServer/1.0\n" +
							"\r\n";
					proxyToClientOut.write(response.getBytes());
					proxyToClientOut.flush();
				} else {
					response = "HTTP/1.0 200 OK\n" +
							"Proxy-agent: ProxyServer/1.0\n" +
							"\r\n";
					proxyToClientOut.write(response.getBytes());
					proxyToClientOut.flush();
					ImageIO.write(image, fileExtension.substring(1), proxyToClientSocket.getOutputStream());
				}
			} 
			
			// Standard text based file requested
			else {
				BufferedReader cachedFileBufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(cachedFile)));

				response = "HTTP/1.0 200 OK\n" +
						"Proxy-agent: ProxyServer/1.0\n" +
						"\r\n";
				proxyToClientOut.write(response.getBytes());
				proxyToClientOut.flush();

				String line;
				while((line = cachedFileBufferedReader.readLine()) != null){
					//Writing for the client
					proxyToClientOut.write(line.getBytes());
				}
				proxyToClientOut.flush();
				// Close resources
				if(cachedFileBufferedReader != null){
					cachedFileBufferedReader.close();
				}	
			}
			// Close Down Resources
			if(proxyToClientOut != null){
				proxyToClientOut.close();
			}

		} catch (IOException e) {
			System.out.println("Error Sending Cached file to client");
			e.printStackTrace();
		}
	}


	/**
	 * Handles HTTPS requests between client and remote server
	 * @param urlString desired file to be transmitted over https
	 */
	
	
	private void handleHTTPSRequest(URI uri,int port) {
		

		try{
			// Only first line of HTTPS request has been read at this point (CONNECT *)
			// Read (and throw away) the rest of the initial data on the stream
			//for(int i=0;i<5;i++){
			//	proxyToClientIn.readLine();
		//	}

								
			// Open a socket to the remote server 
			
			Socket proxyToServerSocket = new Socket(uri.getHost(), port);
			//proxyToServerSocket.setSoTimeout(5000);

			// Send Connection established to the client
			String line = "HTTP/1.0 200 Connection established\r\n" +
					"Proxy-Agent: ProxyServer/1.0\r\n" +
					"\r\n";
			proxyToClientOut.write(line.getBytes());
			proxyToClientOut.flush();
			
			//client to server
			
			HttpsCTransfer clientToServerHttps = 
					new HttpsCTransfer(proxyToClientSocket, proxyToServerSocket);
			// server to client
			HttpsSTransfer serverToClientHttps = 
					new HttpsSTransfer(proxyToServerSocket, proxyToClientSocket);
			clientToServerHttps.start();
			serverToClientHttps.start();
			
			/*// Close Down Resources
			if(proxyToServerSocket!=null){
				proxyToServerSocket.close();
			}

			if(proxyToClientSocket!=null){
				proxyToClientSocket.close();
			}*/
			
			
		} catch (SocketTimeoutException e) {
			String strSSLResponse = "HTTP/1.0 200 Connection established\r\n\r\n";
			try{
				proxyToClientOut.write(strSSLResponse.getBytes());
				proxyToClientOut.flush();
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		} 
		catch (Exception e){
			System.out.println("Error on HTTPS : " + host );
			e.printStackTrace();
		}
	
}
	/*private void handleHTTPSRequest(String urlString){	
		
		
		// Extract the URL and port of remote 
		String url = urlString.substring(7);
		String pieces[] = url.split(":");
		url = pieces[0];
		int port  = Integer.valueOf(pieces[1]);

		try{
			// Only first line of HTTPS request has been read at this point (CONNECT *)
			// Read (and throw away) the rest of the initial data on the stream
			 
			for(int i=0;i<5;i++){
				proxyToClientBr.readLine();
			}

			// Get actual IP associated with this URL through DNS
			InetAddress address = InetAddress.getByName(url);
			
			// Open a socket to the remote server 
			Socket proxyToServerSocket = new Socket(address, port);
			proxyToServerSocket.setSoTimeout(5000);

			// Send Connection established to the client
			String line = "HTTP/1.0 200 Connection established\r\n" +
					"Proxy-Agent: ProxyServer/1.0\r\n" +
					"\r\n";
			proxyToClientBw.write(line);
			proxyToClientBw.flush();
			
			
			
			// Client and Remote will both start sending data to proxy at this point
			// Proxy needs to asynchronously read data from each party and send it to the other party


			//Create a Buffered Writer betwen proxy and remote
			BufferedWriter proxyToServerBW = new BufferedWriter(new OutputStreamWriter(proxyToServerSocket.getOutputStream()));

			// Create Buffered Reader from proxy and remote
			BufferedReader proxyToServerBR = new BufferedReader(new InputStreamReader(proxyToServerSocket.getInputStream()));



			// Create a new thread to listen to client and transmit to server
			ClientToServerHttpsTransmit clientToServerHttps = new ClientToServerHttpsTransmit(clientSocket.getInputStream(), proxyToServerSocket.getOutputStream());
			
			httpsClientToServer = new Thread(clientToServerHttps);
			httpsClientToServer.start();
			
			
			// Listen to remote server and relay to client
			try {
				byte[] buffer = new byte[4096];
				int read;
				do {
					read = proxyToServerSocket.getInputStream().read(buffer);
					if (read > 0) {
						clientSocket.getOutputStream().write(buffer, 0, read);
						if (proxyToServerSocket.getInputStream().available() < 1) {
							clientSocket.getOutputStream().flush();
						}
					}
				} while (read >= 0);
			}
			catch (SocketTimeoutException e) {
				
			}
			catch (IOException e) {
				e.printStackTrace();
			}


			// Close Down Resources
			if(proxyToServerSocket != null){
				proxyToServerSocket.close();
			}

			if(proxyToServerBR != null){
				proxyToServerBR.close();
			}

			if(proxyToServerBW != null){
				proxyToServerBW.close();
			}

			if(proxyToClientBw != null){
				proxyToClientBw.close();
			}
			
			
		} catch (SocketTimeoutException e) {
			String line = "HTTP/1.0 504 Timeout Occured after 10s\n" +
					"User-Agent: ProxyServer/1.0\n" +
					"\r\n";
			try{
				proxyToClientBw.write(line);
				proxyToClientBw.flush();
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		} 
		catch (Exception e){
			System.out.println("Error on HTTPS : " + urlString );
			e.printStackTrace();
		}
	}

	


	/**
	 * Listen to data from client and transmits it to server.
	 * This is done on a separate thread as must be done 
	 * asynchronously to reading data from server and transmitting 
	 * that data to the client. 
	 */
	/*
	class ClientToServerHttpsTransmit implements Runnable{
		
		InputStream proxyToClientIS;
		OutputStream proxyToServerOS;
		
		/**
		 * Creates Object to Listen to Client and Transmit that data to the server
		 * @param proxyToClientIS Stream that proxy uses to receive data from client
		 * @param proxyToServerOS Stream that proxy uses to transmit data to remote server
		 */
	/*
		public ClientToServerHttpsTransmit(InputStream proxyToClientIS, OutputStream proxyToServerOS) {
			this.proxyToClientIS = proxyToClientIS;
			this.proxyToServerOS = proxyToServerOS;
		}

		@Override
		public void run(){
			try {
				// Read byte by byte from client and send directly to server
				byte[] buffer = new byte[4096];
				int read;
				do {
					read = proxyToClientIS.read(buffer);
					if (read > 0) {
						proxyToServerOS.write(buffer, 0, read);
						if (proxyToClientIS.available() < 1) {
							proxyToServerOS.flush();
						}
					}
				} while (read >= 0);
			}
			catch (SocketTimeoutException ste) {
				// TODO: handle exception
			}
			catch (IOException e) {
				System.out.println("Proxy to client HTTPS read timed out");
				e.printStackTrace();
			}
		}
	}

	
	/**
	 * This method is called when user requests a page that is blocked by the proxy.
	 * Sends an access forbidden message back to the client
	 */
	private void blockedSiteRequested(){
		
		try {
			//403
			String line = "HTTP/1.0 403 Access Forbidden \n" +
					"User-Agent: ProxyServer/1.0\n" +
					"\r\n";
			proxyToClientOut.write(line.getBytes());
			proxyToClientOut.flush();
			
		} catch (IOException e) {
			System.out.println("Error writing to client when requested a blocked site");
			e.printStackTrace();
		}
	}
}




