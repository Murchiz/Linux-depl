package com.linuxdeployer.terminal;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class TerminalView extends ScrollView {
    private TextView outputView;
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;

    public TerminalView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setBackgroundColor(Color.BLACK);

        outputView = new TextView(context);
        outputView.setTextColor(Color.GREEN);
        outputView.setTypeface(Typeface.MONOSPACE);
        outputView.setTextSize(12);
        addView(outputView);

        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    public void startShell(String command) {
        try {
            process = Runtime.getRuntime().exec("su");
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            // Start chroot
            writer.write(command + "\n");
            writer.flush();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            final String finalLine = line;
                            outputView.post(new Runnable() {
                                @Override
                                public void run() {
                                    outputView.append(finalLine + "\n");
                                    fullScroll(ScrollView.FOCUS_DOWN);
                                }
                            });
                        }
                    } catch (Exception e) {}
                }
            }).start();
        } catch (Exception e) {
            outputView.append("Error: " + e.getMessage());
        }
    }

    public void writeCommand(String cmd) {
        try {
            writer.write(cmd + "\n");
            writer.flush();
        } catch (Exception e) {}
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            // This is a simple implementation; a real one would capture the current line
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
