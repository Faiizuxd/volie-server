import io.javalin.Javalin;
import io.javalin.http.UploadedFile;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class App {
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .build();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    public static void main(String[] args) {
        // Render compatibility: Get port from Environment Variable
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        Javalin app = Javalin.create().start(port);

        // --- DASHBOARD UI ---
        app.get("/", ctx -> ctx.html(renderUI()));

        // --- BOT START LOGIC ---
        app.post("/start", ctx -> {
            String cookies = ctx.formParam("cookies");
            String threadId = ctx.formParam("threadId");
            String prefix = ctx.formParam("prefix");
            int delay = Integer.parseInt(ctx.formParam("delay"));
            
            UploadedFile file = ctx.uploadedFile("txtFile");
            if (file == null) {
                ctx.result("Please upload a .txt file!");
                return;
            }

            List<String> messages = new BufferedReader(new InputStreamReader(file.content()))
                    .lines().filter(line -> !line.trim().isEmpty())
                    .collect(Collectors.toList());

            // Run process in background thread
            startSendingProcess(cookies, threadId, prefix, delay, messages);

            ctx.html("<h2>Tool Started!</h2><p>Messages are being sent in background.</p><a href='/'>Back</a>");
        });
    }

    private static void startSendingProcess(String cookies, String tid, String prefix, int delay, List<String> messages) {
        final int[] index = {0};
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (index[0] >= messages.size()) index[0] = 0; // Restart list if finished
                
                String fullMsg = prefix + " " + messages.get(index[0]);
                boolean status = executeSend(cookies, tid, fullMsg);
                
                if (status) {
                    System.out.println("[SUCCESS] Sent: " + fullMsg);
                } else {
                    System.out.println("[FAILED] Cookie expired or Network Issue.");
                }
                index[0]++;
            } catch (Exception e) {
                System.out.println("[ERROR] Exception occurred: " + e.getMessage());
            }
        }, 0, delay, TimeUnit.SECONDS);
    }

    private static boolean executeSend(String cookies, String tid, String msg) throws Exception {
        String ua = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36";
        
        // 1. Fetch fb_dtsg from thread page
        Request getRequest = new Request.Builder()
                .url("https://mbasic.facebook.com/messages/read/?tid=" + tid)
                .header("Cookie", cookies)
                .header("User-Agent", ua)
                .build();

        String dtsg = "";
        try (Response response = client.newCall(getRequest).execute()) {
            if (!response.isSuccessful()) return false;
            Document doc = Jsoup.parse(response.body().string());
            dtsg = doc.select("input[name=fb_dtsg]").val();
        }

        if (dtsg == null || dtsg.isEmpty()) return false;

        // 2. Post the message
        RequestBody body = new FormBody.Builder()
                .add("fb_dtsg", dtsg)
                .add("body", msg)
                .add("send", "Send")
                .build();

        Request postRequest = new Request.Builder()
                .url("https://mbasic.facebook.com/messages/send/?icm=1&tid=" + tid)
                .header("Cookie", cookies)
                .header("User-Agent", ua)
                .header("Referer", "https://mbasic.facebook.com/messages/read/?tid=" + tid)
                .post(body)
                .build();

        try (Response response = client.newCall(postRequest).execute()) {
            return response.isSuccessful() && response.body().string().contains("id=\"message_");
        }
    }

    private static String renderUI() {
        return "<html><head><title>FAIZU | FB COOKIE TOOL</title>" +
               "<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css' rel='stylesheet'>" +
               "<style>body{background:#0f0f0f; color:#00ff88; font-family:sans-serif; padding:40px;}" +
               ".card{background:#1a1a1a; border:1px solid #00ff88; padding:20px; border-radius:15px; box-shadow:0 0 15px #00ff88;}</style></head>" +
               "<body><div class='container' style='max-width:600px;'>" +
               "<div class='card'> <h2 class='text-center'>FAIZU COOKIE SENDER</h2> <hr>" +
               "<form action='/start' method='post' enctype='multipart/form-data'>" +
               "<div class='mb-3'><label>Facebook Cookies:</label><textarea name='cookies' class='form-control bg-dark text-white' rows='3' placeholder='datr=...; c_user=...; xs=...' required></textarea></div>" +
               "<div class='mb-3'><label>Thread ID:</label><input type='text' name='threadId' class='form-control bg-dark text-white' required></div>" +
               "<div class='mb-3'><label>Hater Name:</label><input type='text' name='prefix' class='form-control bg-dark text-white' required></div>" +
               "<div class='mb-3'><label>Messages File (.txt):</label><input type='file' name='txtFile' class='form-control bg-dark text-white' required></div>" +
               "<div class='mb-3'><label>Delay (Seconds):</label><input type='number' name='delay' class='form-control bg-dark text-white' value='5' required></div>" +
               "<button type='submit' class='btn btn-success w-100'>START MESSAGING</button>" +
               "</form></div></div></body></html>";
    }
                                         }
