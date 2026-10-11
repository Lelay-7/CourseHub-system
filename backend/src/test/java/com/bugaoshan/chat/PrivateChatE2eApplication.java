package com.bugaoshan.chat;
import com.bugaoshan.BugaoshanApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import javax.sql.DataSource;
import java.net.URI;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.Base64;
/** Test launcher with no Spring annotations; never scan the rest of test-classes. */
public final class PrivateChatE2eApplication {
    private PrivateChatE2eApplication() {}
    public static DataSource guardedSource() throws Exception {
        String url=required("COURSEHUB_CHAT_MYSQL_URL"),nonce=required("COURSEHUB_CHAT_MYSQL_NONCE");
        URI uri=URI.create(url.substring("jdbc:".length()));
        require(url.startsWith("jdbc:mysql://") && "127.0.0.1".equals(uri.getHost()) && uri.getPort()>=1024 && uri.getPort()<=65535 && uri.getUserInfo()==null && uri.getFragment()==null && uri.getPath().matches("/coursehub_chat_check_[a-f0-9]{32}"),"Refusing non-disposable database");
        require(nonce.matches("[a-f0-9]{32}") && "root".equals(required("COURSEHUB_CHAT_MYSQL_USER")) && (System.getenv("COURSEHUB_CHAT_MYSQL_PASSWORD")==null || System.getenv("COURSEHUB_CHAT_MYSQL_PASSWORD").isEmpty()),"Invalid disposable credentials");
        var source=new DriverManagerDataSource(url,"root","");
        try(var c=source.getConnection();var s=c.createStatement();var rows=s.executeQuery("SELECT nonce FROM private_chat_test_guard")) {
            require(c.getMetaData().getDatabaseMajorVersion()==8 && rows.next() && nonce.equals(rows.getString(1)) && !rows.next(),"Disposable guard does not match");
        }
        return source;
    }
    public static void main(String[] args) throws Exception {
        DataSource source=guardedSource();
        int port=Integer.parseInt(required("COURSEHUB_CHAT_E2E_PORT"));
        require(port>=1024 && port<=65535 && port!=URI.create(required("COURSEHUB_CHAT_MYSQL_URL").substring(5)).getPort(),"Invalid API port");
        Path evidence=Path.of(required("COURSEHUB_CHAT_E2E_EVIDENCE_PATH")).toRealPath();
        String run=URI.create(required("COURSEHUB_CHAT_MYSQL_URL").substring(5)).getPath().substring("/coursehub_chat_check_".length());
        require(Files.isDirectory(evidence) && !Files.isSymbolicLink(evidence) && evidence.getFileName().toString().equals("coursehub-chat-mysql-"+run) && evidence.getParent().getFileName().toString().equals("target") && evidence.getParent().getParent().getFileName().toString().equals("backend"),"Invalid evidence directory");
        try(var c=source.getConnection()) {
            var encoder=new BCryptPasswordEncoder();
            for(String name:new String[]{"chat_a","chat_b","chat_admin"}) try(var s=c.prepareStatement("INSERT INTO user(username,password_hash,nickname,user_type,status,credential_version) VALUES(?,?,?,?,1,0)")) {
                s.setString(1,name);s.setString(2,encoder.encode("Chat-old-123"));s.setString(3,name.equals("chat_a")?"同学甲":name.equals("chat_b")?"同学乙":"管理员丙");s.setInt(4,name.equals("chat_admin")?3:1);require(s.executeUpdate()==1,"Fixture insertion failed");
            }
        }
        SpringApplication.run(BugaoshanApplication.class,
            "--server.address=127.0.0.1","--server.port="+port,
            "--spring.datasource.url="+required("COURSEHUB_CHAT_MYSQL_URL"),"--spring.datasource.username=root","--spring.datasource.password=",
            "--spring.datasource.hikari.connection-timeout=5000","--recovery.enabled=false","--spring.mail.host=","--spring.mail.username=","--spring.mail.password=",
            "--jwt.secret="+key(),"--jwt.expiration=86400000","--jwt.refresh-expiration=604800000",
            "--app.chat.send-per-minute=100","--app.chat.send-per-hour=1000",
            "--file.upload.upload-path="+evidence.resolve("runtime-uploads"),"--sms.mock=false","--spring.main.banner-mode=off",
            "--logging.level.root=WARN","--logging.level.com.bugaoshan=WARN","--debug=false","--trace=false");
        System.out.println("Private chat production backend ready on loopback port "+port);
    }
    private static String key(){byte[] bytes=new byte[48];new SecureRandom().nextBytes(bytes);return Base64.getEncoder().encodeToString(bytes);}
    private static String required(String name){String value=System.getenv(name);require(value!=null&&!value.isBlank(),"Missing runner variable "+name);return value;}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
