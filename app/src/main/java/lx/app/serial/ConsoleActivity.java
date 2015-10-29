package lx.app.serial;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConsoleActivity extends Activity {
    public static final String EXTRA_USB_DEVICE = "lx.app.serial.USB_DEVICE";
    public static final String EXTRA_PORT_NUMBER = "lx.app.serial.PORT_NUMBER";
    private static final String TAG = ConsoleActivity.class.getSimpleName();

    private static final int REQUEST_CODE_PREFERENCE = 0;

    private ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private UsbSerialPortManager mManager;
    private UsbSerialPort mPort;
    private boolean mOpen;

    private EditText mEtWrite;
    private Button mBtWrite;
    private ScrollView mSvSerial;
    private TextView mTvSerial;

    private int mBaudRate;
    private int mDataBits;
    private int mParity;
    private int mStopBits;
    private int mFlowControl;
    private int mFontSize;
    private String mTypeface;
    private String mView;

    private SerialInputOutputManager mSerialIoManager;
    private final SerialInputOutputManager.Listener mSerialIoListener =
            new SerialInputOutputManager.Listener() {

                @Override
                public void onNewData(final byte[] data) {
                    ConsoleActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ConsoleActivity.this.onNewData(data);
                        }
                    });
                }

                @Override
                public void onRunError(Exception e) {
                    // TODO
                    Toast.makeText(ConsoleActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_console);

        mEtWrite = (EditText) findViewById(R.id.et_write);
        mBtWrite = (Button) findViewById(R.id.bt_write);
        mSvSerial = (ScrollView) findViewById(R.id.sv_serial);
        mTvSerial = (TextView) findViewById(R.id.tv_serial);
        setPortState(false);

        mManager = UsbSerialPortManager.getInstance(this);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                actionBar.setHomeButtonEnabled(false);
            }
        }

        Intent intent = getIntent();
        UsbDevice device = intent.getParcelableExtra(EXTRA_USB_DEVICE);
        if (device != null) {
            int portNumber = intent.getIntExtra(EXTRA_PORT_NUMBER, 0);
            mPort = mManager.findPort(device, portNumber);

            if (mPort != null && actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                    actionBar.setHomeButtonEnabled(true);
                }
            }
        }

        mEtWrite.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_UP
                        && keyCode == KeyEvent.KEYCODE_ENTER) {
                    writeDataToSerial();
                    return true;
                }
                return false;
            }
        });

        mBtWrite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeDataToSerial();
            }
        });

        loadSettings();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.console_activity_actions, menu);
        if (!"lite".equals(BuildConfig.FLAVOR)) {
            MenuItem item = menu.findItem(R.id.action_about);
            if (item != null)
                item.setVisible(false);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_open:
                openPort();
                return true;
            case R.id.action_close:
                closePort();
                return true;
            case R.id.action_clear:
                mTvSerial.setText("");
                return true;
            case R.id.action_setting:
                startActivityForResult(new Intent(this, ConsoleSettingsActivity.class),
                        REQUEST_CODE_PREFERENCE);
                return true;
            case R.id.action_about:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        openPort();
    }

    @Override
    protected void onStop() {
        closePort();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mPort = null;
        mExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_PREFERENCE) {
            loadSettings();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void loadSettings() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);

        mBaudRate = getStringAsInt(pref, "baudrate", 9600);
        mDataBits = getStringAsInt(pref, "databits", UsbSerialPort.DATABITS_8);
        mParity = getStringAsInt(pref, "parity", UsbSerialPort.PARITY_NONE);
        mStopBits = getStringAsInt(pref, "stopbits", UsbSerialPort.STOPBITS_1);
        mFlowControl = getStringAsInt(pref, "flowcontrol", UsbSerialPort.FLOWCONTROL_NONE);

        int fontsize = getStringAsInt(pref, "fontsize", 12);
        if (fontsize != mFontSize) {
            mFontSize = fontsize;
            mTvSerial.setTextSize(fontsize);
        }

        String typeface = pref.getString("typeface", null);
        if (typeface != null && !typeface.equals(mTypeface)) {
            mTypeface = typeface;
            Typeface tf = null;
            switch (typeface) {
                case "monospace":
                    tf = Typeface.MONOSPACE;
                    break;
                case "normal":
                    tf = Typeface.DEFAULT;
                    break;
                case "sans":
                    tf = Typeface.SANS_SERIF;
                    break;
                case "serif":
                    tf = Typeface.SERIF;
                    break;
                default:
                    break;
            }
            if (tf != null) {
                mTvSerial.setTypeface(tf);
                mEtWrite.setTypeface(tf);
            }
        }

        String view = pref.getString("view", null);
        if (view != null && !view.equals(mView)) {
            mView = view;
//            mTvSerial.setText("");
        }
    }

    private void setPortState(boolean open) {
        mOpen = open;
        mBtWrite.setEnabled(open);
        mEtWrite.setEnabled(open);
    }

    private void onNewData(byte[] data) {
        if ("hex".equalsIgnoreCase(mView)) {
            mTvSerial.append(HexDump.dumpHexString(data));
        } else {
            mTvSerial.append(new String(data));
        }
        mSvSerial.smoothScrollTo(0, mTvSerial.getBottom());
    }

    private void openPort() {
        if (mOpen) return;

        if (mPort == null) {
            Toast.makeText(this, "No serial device.", Toast.LENGTH_SHORT).show();
        } else {
            setTitle(UsbSerialPortManager.getName(mPort.getDriver().getDevice()));

            UsbDeviceConnection connection = mManager.getUsbManager().openDevice(
                    mPort.getDriver().getDevice());
            if (connection == null) {
                Toast.makeText(this, "Opening device failed.", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                mPort.open(connection);
                mPort.setParameters(mBaudRate, mDataBits, mStopBits, mParity);

                mPort.setDTR(mFlowControl != UsbSerialPort.FLOWCONTROL_NONE);
                mPort.setRTS(mFlowControl != UsbSerialPort.FLOWCONTROL_NONE);
            } catch (IOException e) {
                Toast.makeText(this, "Error opening device: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                closePort();
                mPort = null;
                return;
            }

            Toast.makeText(this, "Connected.", Toast.LENGTH_SHORT).show();
        }

        onDeviceStateChange();
    }

    private void closePort() {
        stopIoManager();

        if (mPort != null) {
            try {
                mPort.close();
            } catch (IOException e) {
                // ignore
            }
        }

        if (mOpen) {
            Toast.makeText(this, "Disconnected.", Toast.LENGTH_SHORT).show();
            setPortState(false);
        }
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            if (BuildConfig.DEBUG)
                Log.d(TAG, "Stopping SerialIoManager.");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (mPort != null) {
            setPortState(true);
            if (BuildConfig.DEBUG)
                Log.d(TAG, "Submitting SerialIoManager.");
            mSerialIoManager = new SerialInputOutputManager(mPort, mSerialIoListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    private void writeDataToSerial() {
        SerialInputOutputManager m = mSerialIoManager;
        if (m != null) {
            String text = mEtWrite.getText().toString();
            try {
                text = unescapeJava(text);
            } catch (IOException e) {
                text = "";
            }

            m.writeAsync(text.getBytes());
        }
    }

    private static int getStringAsInt(SharedPreferences pref, String key, int def) {
        String val = pref.getString(key, null);
        if (val != null) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return def;
    }

    private static String unescapeJava(String src) throws IOException {
        if (src == null) {
            return "";
        }
        int sz = src.length();
        StringBuilder unicode = new StringBuilder(4);

        StringBuilder strout = new StringBuilder();
        boolean hadSlash = false;
        boolean inUnicode = false;
        for (int i = 0; i < sz; i++) {
            char ch = src.charAt(i);
            if (inUnicode) {
                // if in unicode, then we're reading unicode
                // values in somehow
                unicode.append(ch);
                if (unicode.length() == 4) {
                    // unicode now contains the four hex digits
                    // which represents our unicode character
                    try {
                        int value = Integer.parseInt(unicode.toString(), 16);
                        strout.append((char) value);
                        unicode.setLength(0);
                        inUnicode = false;
                        hadSlash = false;
                    } catch (NumberFormatException nfe) {
                        // throw new NestableRuntimeException("Unable to parse unicode value: " + unicode, nfe);
                        throw new IOException("Unable to parse unicode value: " + unicode, nfe);
                    }
                }
                continue;
            }
            if (hadSlash) {
                // handle an escaped value
                hadSlash = false;
                switch (ch) {
                    case '\\':
                        strout.append('\\');
                        break;
                    case '\'':
                        strout.append('\'');
                        break;
                    case '\"':
                        strout.append('"');
                        break;
                    case 'r':
                        strout.append('\r');
                        break;
                    case 'f':
                        strout.append('\f');
                        break;
                    case 't':
                        strout.append('\t');
                        break;
                    case 'n':
                        strout.append('\n');
                        break;
                    case 'b':
                        strout.append('\b');
                        break;
                    case 'u':
                    {
                        // uh-oh, we're in unicode country....
                        inUnicode = true;
                        break;
                    }
                    default :
                        strout.append(ch);
                        break;
                }
                continue;
            } else if (ch == '\\') {
                hadSlash = true;
                continue;
            }
            strout.append(ch);
        }
        if (hadSlash) {
            // then we're in the weird case of a \ at the end of the
            // string, let's output it anyway.
            strout.append('\\');
        }
        return strout.toString();
    }
}
