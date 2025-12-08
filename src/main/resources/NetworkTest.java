import java.net.Socket;
import java.net.InetSocketAddress;

public class NetworkTest {

    public static void main(String[] args) {
        System.out.println("开始网络操作测试");
        
        try {
            // 尝试连接外部网络
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress("8.8.8.8", 53), 5000);
            
            System.out.println("网络连接成功");
            socket.close();
            
        } catch (Exception e) {
            System.out.println("网络连接失败: " + e.getMessage());
        }
        
        try {
            // 尝试创建本地服务器
            java.net.ServerSocket serverSocket = new java.net.ServerSocket(8080);
            System.out.println("本地服务器创建成功，端口: 8080");
            serverSocket.close();
            
        } catch (Exception e) {
            System.out.println("本地服务器创建失败: " + e.getMessage());
        }
        
        System.out.println("网络操作测试完成");
    }
} 