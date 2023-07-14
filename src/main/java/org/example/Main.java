package org.example;


import com.sun.media.jfxmedia.events.PlayerEvent;
import sun.misc.BASE64Decoder;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;




public class Main {

    public static class Packet{
        public Payload payload;

        public Packet(Payload payload) {
            this.payload = payload;
        }

        public Packet() {
            this.payload = null;
        }

        public String encoder(){
            byte[] p = payload.encoder();
            byte[] res = new byte[p.length + 2];
            res[0] = (byte)p.length;
            System.arraycopy(p, 0, res, 1, p.length);
            res[p.length + 1] = calculateCRC8(p);
            return(Base64.getUrlEncoder().encodeToString(res));
        }

        public boolean decoder(byte[] payloadWithSum){
            byte[] payload = Arrays.copyOfRange(payloadWithSum, 0, payloadWithSum.length - 1);
            byte controlSum = calculateCRC8(payload);
            int i = 0;
            int[] src = new int[1];
            int[] dst = new int[1];
            int[] serial = new int[1];
            i = Payload.decode128(src, i, payload);
            i = Payload.decode128(dst, i, payload);
            i = Payload.decode128(serial, i, payload);
            byte dev_type = payload[i];
            i++;
            byte cmd = payload[i];
            i++;
            byte[] cmd_body = Arrays.copyOfRange(payload, i, payload.length - 1);
            byte controlSumInPacket = payloadWithSum[payloadWithSum.length - 1];
            if(controlSumInPacket != controlSum){
                return false;
            }
            this.payload = new Payload(src[0], dst[0], serial[0], dev_type, cmd, cmd_body);
            return true;
        }
    }

    public static class Payload{
        public int src;
        public int dst;
        public int serial;

        public byte dev_type;

        public byte cmd;

        public byte[] cmd_body;

        public static class Str{
            byte[] data;
            public Str(String s){
                data = new byte[s.length() + 1];
                data[0] = (byte) s.length();
                System.arraycopy(s.getBytes(), 0, data, 1, s.length());
            }
        }

        public static class Device {
            public Device(String dev_name, byte[] dev_props) {
                this.dev_name = new Str(dev_name);
                this.dev_props = dev_props;
            }

            Str dev_name;
            byte[] dev_props;

            public byte[] encode(){
                byte[] res = new byte[dev_name.data.length + dev_props.length];
                System.arraycopy(dev_name.data, 0, res, 0, dev_name.data.length);
                System.arraycopy(dev_props, 0, res, dev_name.data.length, dev_props.length);
                return res;
            }
        };

        public Payload(int src, int dst, int serial, byte dev_type, byte cmd, byte[] cmd_body) {
            this.src = src;
            this.dst = dst;
            this.serial = serial;
            this.dev_type = dev_type;
            this.cmd = cmd;
            this.cmd_body = cmd_body;
        }

        public static int encode128(int num, int i, byte[] res) {
            if (num > 127) {
                res[i] = (byte) (128 | (num % 128));
                i++;
                res[i] = (byte) ((num - num % 128) >> 7);
                i++;
                return i;
            } else {
                res[i] = (byte) (num);
                i++;
                return i;
            }
        }

        public static int decode128(int[] num, int i, byte[] res){
            num[0] = (int) res[i];
            i++;
            if (num[0] < 0) {
                int cur = res[i];
                i++;
                num[0] = (num[0] & 0x7f) | ((cur & 0x7f) << 7);
            }
            return i;
        }


        public byte[] encoder(){
            int i = 0;
            int srcAdd = 0, dstAdd = 0, serAdd = 0;
            if(src > 127){
                srcAdd++;
            }
            if(dst > 127){
                dstAdd++;
            }
            if(serial > 128){
                serAdd++;
            }
            byte[] res = new byte[cmd_body.length + 5 + srcAdd + dstAdd + serAdd];
            i = encode128(src, i, res);
            i = encode128(dst, i, res);
            i = encode128(serial, i, res);
            res[i] = dev_type;
            i++;
            res[i] = cmd;
            i++;
            System.arraycopy(cmd_body, 0, res, i, cmd_body.length);
            return res;
        }
    }



    public static class SmartDevice{
        byte dev_type;
        byte[] dev_name;
        int dev_code;

        public SmartDevice(byte type, int dev_code) {
            this.dev_type = type;
            this.dev_code = dev_code;
        }
    }

    public static void whoIsHere(String adressStr, HttpURLConnection httpURLConnection){
        try (DataOutputStream writer = new DataOutputStream(httpURLConnection.getOutputStream())) {
            Payload payload = new Payload(Integer.parseInt(adressStr), 16383, 1, (byte)0x01, (byte)0x01, new Payload.Device("s", new byte[0]).encode());
            String encoded = new Packet(payload).encoder();
            String rightEncoded = encoded.substring(0, encoded.length() - 1);
            writer.write(rightEncoded.getBytes());
            writer.flush();
            writer.close();

            BufferedReader input = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()));
            String line = input.readLine();
            byte[] decoded_line = Base64.getUrlDecoder().decode(line);
            //Packet packet = new Packet();
            //packet.decoder(line);
            int responceCode = httpURLConnection.getResponseCode();
            if(responceCode == 200){
                int i = 0;
                while(i < decoded_line.length){
                    int len = decoded_line[i];
                    byte[] current = Arrays.copyOfRange(decoded_line, i + 1, i + len + 2);
                    i = i + len + 2;
                    Packet packet = new Packet();
                    packet.decoder(current);
                    byte[] cmd_body = packet.payload.cmd_body;
                    int dev_name_length = cmd_body[0];
                    //byte[] dev_name = Arrays.copyOfRange(cmd_body, 1, dev_name_length + 1);
                    //byte[] dev_props = Arrays.copyOfRange(cmd_body, dev_name_length + 2, cmd_body.length - 1);
                    SmartDevice newDevice = new SmartDevice(packet.payload.dev_type, payload.src);
                    devices.add(newDevice);
                }
            }
            else if(responceCode == 204){
                System.exit(0);
            }
            else{
                System.exit(99);
            }
        }
        catch(IOException e){
            System.exit(99);
        }
    }
    public static String smartHomeName = "smartHome";
    private static boolean working = true;
    private static ArrayList<SmartDevice> devices = new ArrayList<SmartDevice>();

    public static void main(String[] args) {
        String urlStr = args[0];
        String adressStr = args[1];
        //
        HttpURLConnection httpURLConnection = connect(urlStr, adressStr);
        whoIsHere(adressStr, httpURLConnection);
        /*try (DataOutputStream writer = new DataOutputStream(httpURLConnection.getOutputStream())) {
            int responceCode = httpURLConnection.getResponseCode();
            while(responceCode == 200){

                responceCode = httpURLConnection.getResponseCode();
            }
            if(responceCode == 204){
                System.exit(0);
            }
            else{
                System.exit(99);
            }
            writer.write(rightEncoded.getBytes());
            writer.flush();
            writer.close();
        }
        catch(Exception e){
            System.exit(99);
        }

        Packet packet = new Packet();
        String line = "";
        try (BufferedReader input = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()))) {
            line = input.readLine();
            System.out.println(line);
            packet.decoder(line);
        }
        catch(Exception e){
            System.exit(99);
        }
        finally {
            httpURLConnection.disconnect();
        }*/
        System.exit(0);
    }


    public static HttpURLConnection connect(String urlStr, String adressStr){
        URL url = null;
        HttpURLConnection http = null;
        try {
            url = new URL(urlStr);
            http = (HttpURLConnection) url.openConnection();
            http.setRequestMethod("POST");
            http.setDoOutput(true);
        } catch (IOException e) {
            System.exit(99);
        }
        return http;
    }

    public static byte calculateCRC8(byte [] bytes){
        byte generator = 0x1D;
        byte crc = 0; /* start with 0 so first byte can be 'xored' in */
        for(byte currByte : bytes)
        {
            crc ^= currByte; /* XOR-in the next input byte */

            for (int i = 0; i < 8; i++)
            {
                if ((crc & 0x80) != 0)
                {
                    crc = (byte)((crc << 1) ^ generator);
                }
                else
                {
                    crc <<= 1;
                }
            }
        }

        return crc;
    }
}