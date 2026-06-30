package com.example.app1;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothHandler {

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private ListeningThread listeningThread;
    private BluetoothListener mListener;

    // 블루투스 SPP 표준 UUID (현재는 우회 연결을 사용하여 참고용으로만 남겨둠)
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public interface BluetoothListener {
        void onBitPatternReceived(String pattern);
    }

    public void setBluetoothListener(BluetoothListener listener) {
        this.mListener = listener;
    }

    public BluetoothHandler() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public boolean connect(String deviceAddress) {
        if (bluetoothAdapter == null || deviceAddress == null) {
            Log.e("BT_ERROR", "블루투스 어댑터가 없거나 주소가 null입니다.");
            return false;
        }

        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            // 기존 연결이 있다면 안전하게 닫기
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }

            // 💡 [수정됨] UUID 매칭 실패를 방지하기 위해 라즈베리파이의 1번 포트로 다이렉트 강제 연결 (Reflection 방식)
            Log.d("BT_STATUS", "라즈베리파이 1번 채널로 다이렉트 연결 시도 중...");
            bluetoothSocket = (BluetoothSocket) device.getClass().getMethod("createRfcommSocket", new Class[] {int.class}).invoke(device, 1);

            // 소켓 연결 실행
            bluetoothSocket.connect();

            outputStream = bluetoothSocket.getOutputStream();
            inputStream = bluetoothSocket.getInputStream();

            // 연결 성공 시 수신 쓰레드 자동 시작
            if (listeningThread != null) {
                listeningThread.interrupt();
            }
            listeningThread = new ListeningThread();
            listeningThread.start();

            Log.d("BT_SUCCESS", "🎉 라즈베리파이와 블루투스 연결 성공!");
            return true;

        } catch (SecurityException se) {
            // 💡 [추가됨] 안드로이드 12 이상에서 BLUETOOTH_CONNECT 권한이 없을 때 발생하는 에러
            Log.e("BT_ERROR", "🚨 권한 에러! 안드로이드 앱에 블루투스 연결 권한이 없습니다.", se);
            close();
            return false;
        } catch (Exception e) {
            // 💡 [추가됨] 기기가 꺼져있거나, 거리가 멀거나, 주소가 틀렸을 때 발생하는 에러
            Log.e("BT_ERROR", "🚨 블루투스 연결 실패! 기기 상태나 MAC 주소를 확인하세요.", e);
            close();
            return false;
        }
    }

    // 1. 학습/AI 모드용 한글 텍스트 전송 (Braillify 번역용)
    public void sendStudyChar(String charName) {
        if (outputStream == null || charName == null) return;
        try {
            String message = "STUDY:" + charName + "\n";
            outputStream.write(message.getBytes("UTF-8"));
            outputStream.flush();
            Log.d("BT_SEND", "전송 완료: " + message.trim());
        } catch (Exception e) {
            Log.e("BT_ERROR", "데이터 전송 실패 (sendStudyChar)", e);
        }
    }

    // 2. 시험 모드용 비트 패턴 직접 전송 (번역 생략, 핀 바로 제어)
    public void sendData(int[] pattern) {
        if (outputStream == null || pattern == null) return;
        StringBuilder sb = new StringBuilder();
        for (int bit : pattern) sb.append(bit);
        sb.append("\n");

        try {
            outputStream.write(sb.toString().getBytes("UTF-8"));
            outputStream.flush();
            Log.d("BT_SEND", "전송 완료: " + sb.toString().trim());
        } catch (Exception e) {
            Log.e("BT_ERROR", "데이터 전송 실패 (sendData)", e);
        }
    }

    // 3. 라즈베리파이 마이크 끄기 신호
    public void sendMicOff() {
        if (outputStream == null) return;
        try {
            outputStream.write("CMD:MIC_OFF\n".getBytes("UTF-8"));
            outputStream.flush();
            Log.d("BT_SEND", "명령 전송: 마이크 OFF");
        } catch (Exception e) {
            Log.e("BT_ERROR", "마이크 OFF 명령 전송 실패", e);
        }
    }

    // 4. 라즈베리파이 마이크 켜기 신호
    public void sendMicOn() {
        if (outputStream == null) return;
        try {
            outputStream.write("CMD:MIC_ON\n".getBytes("UTF-8"));
            outputStream.flush();
            Log.d("BT_SEND", "명령 전송: 마이크 ON");
        } catch (Exception e) {
            Log.e("BT_ERROR", "마이크 ON 명령 전송 실패", e);
        }
    }

    // 통신 종료 및 자원 정리
    public void close() {
        try {
            if (listeningThread != null) listeningThread.interrupt();
            if (outputStream != null) outputStream.close();
            if (inputStream != null) inputStream.close();
            if (bluetoothSocket != null) bluetoothSocket.close();
            Log.d("BT_STATUS", "블루투스 자원 안전하게 해제됨");
        } catch (Exception e) {
            Log.e("BT_ERROR", "자원 해제 중 에러 발생", e);
        }
    }

    // 파이에서 쏴주는 비트 데이터를 백그라운드에서 수신하는 쓰레드
    private class ListeningThread extends Thread {
        @Override
        public void run() {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String receivedLine = reader.readLine();
                    if (receivedLine != null && mListener != null) {
                        Log.d("BT_RECEIVE", "수신된 데이터: " + receivedLine.trim());
                        mListener.onBitPatternReceived(receivedLine.trim());
                    }
                } catch (Exception e) {
                    Log.e("BT_ERROR", "데이터 수신 중단됨", e);
                    break;
                }
            }
        }
    }
}