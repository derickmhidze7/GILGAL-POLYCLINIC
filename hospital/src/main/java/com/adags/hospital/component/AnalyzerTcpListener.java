package com.adags.hospital.component;

import com.adags.hospital.dto.lab.AnalyzerResultDto;
import com.adags.hospital.service.lab.AnalyzerService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class AnalyzerTcpListener {

    private static final int  PORT        = 5100;
    private static final int  MLLP_START  = 0x0B; // VT — start of HL7 block
    private static final int  MLLP_END    = 0x1C; // FS — end of HL7 block

    private final AnalyzerService analyzerService;

    public AnalyzerTcpListener(AnalyzerService analyzerService) {
        this.analyzerService = analyzerService;
    }

    @PostConstruct
    public void start() {
        Thread t = new Thread(this::listen, "analyzer-tcp-listener");
        t.setDaemon(true);
        t.start();
    }

    private void listen() {
        try (ServerSocket server = new ServerSocket(PORT)) {
            while (true) {
                try (Socket socket = server.accept()) {
                    String hl7 = readMllp(socket.getInputStream());
                    if (hl7 != null) {
                        AnalyzerResultDto dto = parseHl7(hl7);
                        if (dto != null) {
                            analyzerService.broadcastResult(dto);
                        }
                    }
                } catch (Exception ignored) {
                    // log and keep accepting next connection
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot open analyzer TCP port " + PORT, e);
        }
    }

    /**
     * Reads one MLLP-wrapped HL7 message from the stream.
     * MLLP framing: 0x0B ... message bytes ... 0x1C 0x0D
     */
    private String readMllp(InputStream in) throws IOException {
        // Scan for the MLLP start byte (0x0B)
        int b;
        while ((b = in.read()) != -1 && b != MLLP_START) { /* skip */ }
        if (b == -1) return null;

        // Accumulate bytes until the MLLP end byte (0x1C)
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while ((b = in.read()) != -1 && b != MLLP_END) {
            buf.write(b);
        }
        return buf.toString(StandardCharsets.US_ASCII);
    }

    /**
     * Parses HL7 OBX segments from an ORU^R01 message.
     * Each OBX segment carries one CBC parameter.
     *
     * OBX field positions (1-based, split on |):
     *   [3]  = observation ID, e.g. "WBC^White Blood Cell"
     *   [5]  = value,          e.g. "7.8"
     *   [6]  = units,          e.g. "10^9/L"
     *   [7]  = reference range,e.g. "4.0-10.0"
     *   [8]  = abnormal flag,  N / H / L / C
     */
    private AnalyzerResultDto parseHl7(String hl7) {
        List<AnalyzerResultDto.ParameterRow> rows = new ArrayList<>();
        for (String seg : hl7.split("\r")) {
            if (!seg.startsWith("OBX|")) continue;
            String[] f = seg.split("\\|", -1);
            if (f.length < 9) continue;

            // f[3] = "WBC^White Blood Cell" → code = WBC, name = White Blood Cell
            String[] id  = f[3].split("\\^", -1);
            String code  = id.length > 0 ? id[0].trim() : "";
            String name  = id.length > 1 ? id[1].trim() : code;
            String value = f[5].trim();
            String unit  = f[6].trim();
            String range = f[7].trim();
            String flag  = f[8].trim().isEmpty() ? "N" : f[8].trim();

            rows.add(new AnalyzerResultDto.ParameterRow(code, name, value, unit, range, flag));
        }
        if (rows.isEmpty()) return null;
        return new AnalyzerResultDto(rows);
    }
}
