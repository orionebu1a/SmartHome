package org.example;


import com.sun.media.jfxmedia.events.PlayerEvent;
import sun.misc.BASE64Decoder;
import sun.security.util.ArrayUtil;

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

        public byte[] encoder(){
            byte[] p = payload.encoder();
            byte[] res = new byte[p.length + 2];
            res[0] = (byte)p.length;
            System.arraycopy(p, 0, res, 1, p.length);
            res[p.length + 1] = calculateCRC8(p);
            return(res);
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
            byte[] cmd_body = Arrays.copyOfRange(payload, i, payload.length);
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

        public static int decode128_time(long[] num, int i, byte[] res){
            num[0] = res[i];
            i++;
            if (num[0] < 0) {
                long cur = res[i];
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
                        if (cur < 0) {
                            cur = res[i];
                            i++;
                            num[0] |= (cur & 0x7f) << 28;
                            if (cur < 0) {
                                cur = res[i];
                                i++;
                                num[0] |= (cur & 0x7f) << 35;
                            }
                        }
                    }
                }
            }

            return i;
        }


        public byte[] encoder (){
            int i = 0;
            int srcAdd = 0, dstAdd = 0, serAdd = 0;
            if(src > 127){
                srcAdd++;
            }
            if(dst > 127){
                dstAdd++;
            }
            if(serial > 127){
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

        public String name;
    }

    public static interface SmartDevice{
        DeviceInfo getDeviceInfo();

        void doAction();
    }

    public static class Switch implements SmartDevice{
        private boolean condition;
        private DeviceInfo deviceInfo;

        ArrayList<String> dev_names = new ArrayList<String>();

        public Switch(byte type, int dev_code, byte[] cmd_body, String name) {
            int dev_name_length = cmd_body[0];
            byte[] dev_props = Arrays.copyOfRange(cmd_body, dev_name_length + 2, cmd_body.length);
            int i = 0, length;
            byte[] dev_name;
            while(i < dev_props.length){
                length = dev_props[0];
                dev_name = Arrays.copyOfRange(dev_props, i + 1, i + length + 1);
                String str_name = new String(dev_name);
                dev_names.add(str_name);
                i = i + length + 1;
            }
            this.deviceInfo = new DeviceInfo();
            this.deviceInfo.type = type;
            this.deviceInfo.dev_code = dev_code;
            this.deviceInfo.getStatus = false;
            this.deviceInfo.name = name;
        }

        @Override
        public DeviceInfo getDeviceInfo() {
            return deviceInfo;
        }

        @Override
        public void doAction() {
            for(String turnable : dev_names){
                for(SmartDevice device : devices){
                    if(device.getDeviceInfo().name.equals(turnable)){
                        device.getDeviceInfo().getStatus = false;
                        if(device.getDeviceInfo().type == 0x04){
                            Lamp lamp = (Lamp)device;
                            lamp.condition = condition;
                        }
                        else if(device.getDeviceInfo().type == 0x05){
                            Socket socket = (Socket)device;
                            socket.condition = condition;
                        }
                        Payload payload = new Payload(Integer.parseInt(adressStr), device.getDeviceInfo().dev_code, counter, device.getDeviceInfo().type, (byte)0x05, (condition) ? new byte[]{1} : new byte[]{0});
                        byte[] request = new Packet(payload).encoder();
                        byte[] resRequest = Arrays.copyOf(fullRequest, fullRequest.length + request.length);
                        System.arraycopy(request, 0, resRequest, fullRequest.length, request.length);
                        fullRequest = resRequest;
                    }
                }
            }
        }
    }

    public static class Lamp implements SmartDevice {
        private boolean condition;
        private DeviceInfo deviceInfo;

        public Lamp(byte type, int dev_code, byte[] cmd_body, String name) {
            this.deviceInfo = new DeviceInfo();
            this.deviceInfo.type = type;
            this.deviceInfo.dev_code = dev_code;
            this.deviceInfo.getStatus = false;
            this.deviceInfo.name = name;
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
        private boolean condition;

        private DeviceInfo deviceInfo;

        public Socket(byte type, int dev_code, byte[] cmd_body, String name) {
            this.deviceInfo = new DeviceInfo();
            this.deviceInfo.type = type;
            this.deviceInfo.dev_code = dev_code;
            this.deviceInfo.getStatus = false;
            this.deviceInfo.name = name;
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

        public EnvSensor(byte type, int dev_code, byte[] cmd_body, String name) {
            this.deviceInfo = new DeviceInfo();
            this.deviceInfo.type = type;
            this.deviceInfo.dev_code = dev_code;
            this.deviceInfo.getStatus = false;
            this.deviceInfo.name = name;
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

        public Clock(byte type, int dev_code, byte[] cmd_body, String name) {
            this.deviceInfo = new DeviceInfo();
            this.deviceInfo.type = type;
            this.deviceInfo.dev_code = dev_code;
            this.deviceInfo.getStatus = false;
            this.deviceInfo.name = name;
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
            httpURLConnection = connect(urlStr, adressStr);
            DataOutputStream writer = new DataOutputStream(httpURLConnection.getOutputStream());
            Payload payload = new Payload(Integer.parseInt(adressStr), 16383, counter, (byte)0x01, (byte)0x01, new Payload.Device("s", new byte[0]).encode());
            String encoded = new String(Base64.getUrlEncoder().encode((new Packet(payload)).encoder()));
            String rightEncoded = encoded.substring(0, encoded.length() - 1);
            writer.write(rightEncoded.getBytes());
            writer.flush();
            writer.close();
            counter++;
        }
        catch(Exception e){
            System.exit(99);
        }
    }

    public static void getFirstStatus(SmartDevice device){
        Payload payload = new Payload(Integer.parseInt(adressStr), device.getDeviceInfo().dev_code, counter, device.getDeviceInfo().type, (byte)0x03, new byte[0]);
        byte[] request = new Packet(payload).encoder();
        byte[] resRequest = Arrays.copyOf(fullRequest, fullRequest.length + request.length);
        System.arraycopy(request, 0, resRequest, fullRequest.length, request.length);
        fullRequest = resRequest;
    }

    private static void executePost() {
        if(fullRequest.length == 0){
            return;
        }
        try{
            httpURLConnection = connect(urlStr, adressStr);
            DataOutputStream writer = new DataOutputStream(httpURLConnection.getOutputStream());
            String coded = new String(Base64.getUrlEncoder().encode(fullRequest));
            while(coded.charAt(coded.length() - 1) == '='){
                coded = coded.substring(0, coded.length() - 1);
            }
            writer.write(coded.getBytes());
            writer.flush();
            writer.close();
            lastRequest = fullRequest;
            lastCoded = coded;
            fullRequest = new byte[0];
            counter++;
        }
        catch(Exception e){
            System.exit(99);
        }
    }

    public static void writeTime(Packet packet){
        byte[] cmd_body = packet.payload.cmd_body;
        long[] num = new long[1];
        Payload.decode128_time(num, 0, cmd_body);
        time = new Timestamp(num[0]);
    }

    private static void replyWhoIsHere(Packet packet) {
        Payload payload = new Payload(Integer.parseInt(adressStr), packet.payload.src, counter, (byte)0x01, (byte)0x02, new byte[0]);
        byte[] request = new Packet(payload).encoder();
        byte[] resRequest = Arrays.copyOf(fullRequest, fullRequest.length + request.length);
        System.arraycopy(request, 0, resRequest, fullRequest.length, request.length);
        fullRequest = resRequest;
        addDevice(packet);
    }

    private static void setStatus(Packet packet) {
    }

    private static void status(Packet packet) {
        byte dev_type = packet.payload.dev_type;
        byte[] cmd_body = packet.payload.cmd_body;
        switch (dev_type){
            case 2:
                break;
            case 3:
                for(SmartDevice device : devices){
                    if(device.getDeviceInfo().dev_code == packet.payload.src){
                        Switch deviceSwitch = (Switch)device;
                        if(cmd_body[0] == 0){
                            deviceSwitch.condition = false;
                            deviceSwitch.doAction();
                        }
                        else{
                            deviceSwitch.condition = true;
                            deviceSwitch.doAction();
                        }
                    }
                }
                break;
            case 4:
                for(SmartDevice device : devices){
                    if(device.getDeviceInfo().dev_code == packet.payload.src){
                        Lamp lamp = (Lamp)device;
                        boolean realCondition = packet.payload.cmd_body[0] == 0 ? false : true;
                        if(realCondition != lamp.condition){
                            lamp.deviceInfo.getStatus = false;
                            Payload payload = new Payload(Integer.parseInt(adressStr), lamp.getDeviceInfo().dev_code, counter, lamp.getDeviceInfo().type, (byte)0x05, (lamp.condition) ? new byte[]{1} : new byte[]{0});
                            byte[] request = new Packet(payload).encoder();
                            byte[] resRequest = Arrays.copyOf(fullRequest, fullRequest.length + request.length);
                            System.arraycopy(request, 0, resRequest, fullRequest.length, request.length);
                            fullRequest = resRequest;
                        }
                    }
                }
                break;
            case 5:
                for(SmartDevice device : devices){
                    if(device.getDeviceInfo().dev_code == packet.payload.src){
                        Socket socket = (Socket)device;
                        boolean realCondition = packet.payload.cmd_body[0] == 0 ? false : true;
                        if(realCondition != socket.condition){
                            socket.deviceInfo.getStatus = false;
                            Payload payload = new Payload(Integer.parseInt(adressStr), socket.getDeviceInfo().dev_code, counter, socket.getDeviceInfo().type, (byte)0x05, (socket.condition) ? new byte[]{1} : new byte[]{0});
                            byte[] request = new Packet(payload).encoder();
                            byte[] resRequest = Arrays.copyOf(fullRequest, fullRequest.length + request.length);
                            System.arraycopy(request, 0, resRequest, fullRequest.length, request.length);
                            fullRequest = resRequest;
                        }
                    }
                }
                break;
            case 6:
                break;
        }
    }

    private static void getStatus(Packet packet) {
    }

    public static void addDevice(Packet packet){
        SmartDevice newDevice = null;
        byte[] cmd_body = packet.payload.cmd_body;
        byte dev_type = packet.payload.dev_type;
        int dev_name_length = cmd_body[0];
        byte[] dev_name = Arrays.copyOfRange(cmd_body, 1, dev_name_length + 1);
        switch (dev_type){
            case 2:
                newDevice = new EnvSensor(packet.payload.dev_type, packet.payload.src, cmd_body, new String(dev_name));
                break;
            case 3:
                newDevice = new Switch(packet.payload.dev_type, packet.payload.src, cmd_body, new String(dev_name));
                break;
            case 4:
                newDevice = new Lamp(packet.payload.dev_type, packet.payload.src, cmd_body, new String(dev_name));
                break;
            case 5:
                newDevice = new Socket(packet.payload.dev_type, packet.payload.src, cmd_body, new String(dev_name));
                break;
            case 6:
                newDevice = new Clock(packet.payload.dev_type, packet.payload.src, cmd_body, new String(dev_name));
                break;
        }
        devices.add(newDevice);
    }

    public static String smartHomeName = "smartHome";
    private static boolean working = true;
    private static ArrayList<SmartDevice> devices = new ArrayList<SmartDevice>();

    private static Timestamp time;

    private static int counter = 1;

    private static String adressStr;
    private static HttpURLConnection httpURLConnection;

    private static String urlStr;

    private static byte[] fullRequest = new byte[0];

    private static byte[] lastRequest = new byte[0];

    private static String lastCoded;


    public static void main(String[] args) {
        urlStr = args[0];
        adressStr = args[1];
        httpURLConnection = connect(urlStr, adressStr);
        whoIsHere();
        int responceCode = 200;
        while(responceCode == 200){
            String line = null;
            try {
                BufferedReader input = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()));
                responceCode = httpURLConnection.getResponseCode();
                line = input.readLine();
                input.close();
            }
            catch (Exception e){
                System.exit(99);
            }
            if(line == null){
                httpURLConnection = connect(urlStr, adressStr);
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
                        replyWhoIsHere(packet);
                        break;
                    case 2:
                        addDevice(packet);
                        break;
                    case 3:
                        setStatus(packet);
                        break;
                    case 4:
                        status(packet);
                        break;
                    case 5:
                        getStatus(packet);
                        break;
                    case 6:
                        writeTime(packet);
                        break;
                }
            }
            httpURLConnection = connect(urlStr, adressStr);
            for(SmartDevice device : devices){
                if(device.getDeviceInfo().getStatus == false){
                    device.getDeviceInfo().getStatus = true;
                    getFirstStatus(device);
                }
            }
            executePost();
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