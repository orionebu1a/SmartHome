package org.example;


import com.sun.media.jfxmedia.events.PlayerEvent;
import sun.misc.BASE64Decoder;

import java.io.*;
import java.net.*;
import java.sql.Timestamp;
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

        public static int decode128_time(int[] num, int i, byte[] res){
            num[0] = (int) res[i];
            i++;
            if (num[0] < 0) {
                int cur = res[i];
                i++;
                num[0] = (num[0] & 0x7f) | ((cur & 0x7f) << 7);
            }

            num[0] = (int) res[i];
            i++;
            if (num[0] < 0) {
                int cur = res[i];
                i++;
                num[0] = (num[0] & 0x7f) | ((cur & 0x7f) << 7);
                if (cur < 0) {
                    cur = res[i];
                    i++;
                    num[0] |= (cur & 0x7f) << 14;
                    if (cur < 0) {
                        cur = res[i];
                        i++;
                        num[0] |= (cur & 0x7f) << 21;
                        if (cur > 0x7f) {
                            cur = res[i];
                            i++;
                            num[0] |= cur << 28;
                        }
                    }
                }
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

    public static class DeviceInfo{
        public byte type;
        public int dev_code;
        public boolean getStatus;
    }

    public static interface SmartDevice{
        DeviceInfo getDeviceInfo();

        void doAction();
    }

    public static class Switch implements SmartDevice{
        private DeviceInfo deviceInfo;

        public Switch(byte type, int dev_code, byte[] cmd_body) {
            this.deviceInfo = new DeviceInfo();
            this.deviceInfo.type = type;
            this.deviceInfo.dev_code = dev_code;
            this.deviceInfo.getStatus = false;
        }

        @Override
        public DeviceInfo getDeviceInfo() {
            return deviceInfo;
        }

        @Override
        public void doAction() {

        }
    }

    public static class Lamp implements SmartDevice {
        private DeviceInfo deviceInfo;

        public Lamp(byte type, int dev_code, byte[] cmd_body) {
            this.deviceInfo = new DeviceInfo();
            this.deviceInfo.type = type;
            this.deviceInfo.dev_code = dev_code;
            this.deviceInfo.getStatus = false;
        }

        @Override
        public DeviceInfo getDeviceInfo() {
            return deviceInfo;
        }

        @Override
        public void doAction() {

        }
    }

    public static class Socket implements SmartDevice {

        private DeviceInfo deviceInfo;

        public Socket(byte type, int dev_code, byte[] cmd_body) {
            this.deviceInfo = new DeviceInfo();
            this.deviceInfo.type = type;
            this.deviceInfo.dev_code = dev_code;
            this.deviceInfo.getStatus = false;
        }

        @Override
        public DeviceInfo getDeviceInfo() {
            return deviceInfo;
        }

        @Override
        public void doAction() {

        }
    }

    public static class EnvSensor implements SmartDevice {
        private DeviceInfo deviceInfo;

        public EnvSensor(byte type, int dev_code, byte[] cmd_body) {
            this.deviceInfo = new DeviceInfo();
            this.deviceInfo.type = type;
            this.deviceInfo.dev_code = dev_code;
            this.deviceInfo.getStatus = false;
        }

        @Override
        public DeviceInfo getDeviceInfo() {
            return deviceInfo;
        }

        @Override
        public void doAction() {

        }
    }

    public static class Clock implements SmartDevice {
        private DeviceInfo deviceInfo;

        public Clock(byte type, int dev_code, byte[] cmd_body) {
            this.deviceInfo = new DeviceInfo();
            this.deviceInfo.type = type;
            this.deviceInfo.dev_code = dev_code;
            this.deviceInfo.getStatus = false;
        }

        @Override
        public DeviceInfo getDeviceInfo() {
            return deviceInfo;
        }

        @Override
        public void doAction() {

        }
    }

    public static void whoIsHere(){
        try{
            DataOutputStream writer = new DataOutputStream(httpURLConnection.getOutputStream());
            Payload payload = new Payload(Integer.parseInt(adressStr), 16383, counter, (byte)0x01, (byte)0x01, new Payload.Device("s", new byte[0]).encode());
            String encoded = new Packet(payload).encoder();
            String rightEncoded = encoded.substring(0, encoded.length() - 1);
            writer.write(rightEncoded.getBytes());
            writer.flush();
            writer.close();
            counter++;
        }
        catch(IOException e){
            System.exit(99);
        }
    }

    public static void getFirstStatus(SmartDevice device){
        try{
            httpURLConnection = connect(urlStr, adressStr);
            DataOutputStream writer = new DataOutputStream(httpURLConnection.getOutputStream());
            Payload payload = new Payload(Integer.parseInt(adressStr), device.getDeviceInfo().dev_code, counter, device.getDeviceInfo().type, (byte)0x03, new byte[0]);
            String encoded = new Packet(payload).encoder();
            String rightEncoded = encoded.substring(0, encoded.length() - 1);
            writer.write(rightEncoded.getBytes());
            writer.flush();
            writer.close();
            counter++;
        }
        catch(IOException e){
            System.exit(99);
        }
    }

    public static void writeTime(Packet packet){

    }

    public static void addDevice(Packet packet){
        httpURLConnection = connect(urlStr, adressStr);
        SmartDevice newDevice = null;
        byte[] cmd_body = packet.payload.cmd_body;
        byte dev_type = packet.payload.dev_type;
        //int dev_name_length = cmd_body[0];
        //byte[] dev_name = Arrays.copyOfRange(cmd_body, 1, dev_name_length + 1);
        //byte[] dev_props = Arrays.copyOfRange(cmd_body, dev_name_length + 2, cmd_body.length - 1);
        switch (dev_type){
            case 2:
                newDevice = new EnvSensor(packet.payload.dev_type, packet.payload.src, cmd_body);
                break;
            case 3:
                newDevice = new Switch(packet.payload.dev_type, packet.payload.src, cmd_body);
                break;
            case 4:
                newDevice = new Lamp(packet.payload.dev_type, packet.payload.src, cmd_body);
                break;
            case 5:
                newDevice = new Socket(packet.payload.dev_type, packet.payload.src, cmd_body);
                break;
            case 6:
                newDevice = new Clock(packet.payload.dev_type, packet.payload.src, cmd_body);
                break;
        }
        devices.add(newDevice);
        //getFirstStatus(newDevice);
    }

    public static String smartHomeName = "smartHome";
    private static boolean working = true;
    private static ArrayList<SmartDevice> devices = new ArrayList<SmartDevice>();

    private static Timestamp time;

    private static int counter = 1;

    private static String adressStr;
    private static HttpURLConnection httpURLConnection;

    private static String urlStr;

    //private static DataOutputStream writer;

    public static void main(String[] args) {
        urlStr = args[0];
        adressStr = args[1];
        httpURLConnection = connect(urlStr, adressStr);
        whoIsHere();
        int responceCode = 200;
        while(responceCode == 200){
            String line = null;
            try {
                httpURLConnection = connect(urlStr, adressStr);
                BufferedReader input = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()));
                responceCode = httpURLConnection.getResponseCode();
                line = input.readLine();
                input.close();
            }
            catch (IOException e){
                System.exit(99);
            }
            if(line == null){
                continue;
            }
            byte[] decoded_line = Base64.getUrlDecoder().decode(line);
            int i = 0;
            while(i < decoded_line.length){
                int len = decoded_line[i];
                byte[] current = Arrays.copyOfRange(decoded_line, i + 1, i + len + 2);
                i = i + len + 2;
                Packet packet = new Packet();
                packet.decoder(current);
                switch(packet.payload.cmd){
                    case 1:
                        break;
                    case 2:
                        addDevice(packet);
                        break;
                    case 3:
                        break;
                    case 4:
                        break;
                    case 5:
                        break;
                    case 6:
                        writeTime(packet);
                        break;
                }
                for(SmartDevice device : devices){
                    if(device.getDeviceInfo().getStatus == false){
                        device.getDeviceInfo().getStatus = true;
                        getFirstStatus(device);
                    }
                }
            }
        }
        if(responceCode == 204){
            httpURLConnection.disconnect();
            System.exit(0);
        }
        else{
            httpURLConnection.disconnect();
            System.exit(99);
        }
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