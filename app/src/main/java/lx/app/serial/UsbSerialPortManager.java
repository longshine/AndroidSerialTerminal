package lx.app.serial;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Log;

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.Cp21xxSerialDriver;
import com.hoho.android.usbserial.driver.UsbId;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.ArrayList;
import java.util.List;

public class UsbSerialPortManager {
    private static final String TAG = UsbSerialPortManager.class.getSimpleName();
    private static UsbSerialPortManager ourInstance;

    final Context mContext;
    final UsbManager mUsbManager;
    final List<UsbSerialPort> mPorts = new ArrayList<>();
    Listener mListener;

    /**
     * BroadcastReceiver when insert/remove the device USB plug into/from a USB port.
     */
    final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)
                || UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                refreshPorts();
            }
        }
    };

    public static UsbSerialPortManager getInstance(Context context) {
        if (ourInstance == null) {
            synchronized (UsbSerialPortManager.class) {
                if (ourInstance == null) {
                    ourInstance = new UsbSerialPortManager(context);
                }
            }
        }
        return ourInstance;
    }

    public static void free() {
        if (ourInstance != null) {
            synchronized (UsbSerialPortManager.class) {
                if (ourInstance != null) {
                    ourInstance.destroy();
                    ourInstance = null;
                }
            }
        }
    }

    private UsbSerialPortManager(Context context) {
        mContext = context;
        mUsbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        // listen for new devices
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        context.registerReceiver(mUsbReceiver, filter);
    }

    public UsbManager getUsbManager() {
        return mUsbManager;
    }

    public List<UsbSerialPort> getPorts() {
        return mPorts;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void destroy() {
        mContext.unregisterReceiver(mUsbReceiver);
    }

    public void refreshPorts() {
        List<UsbSerialDriver> drivers = //getDummyDrivers();
                UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);
        List<UsbSerialPort> result = new ArrayList<>();
        for (UsbSerialDriver driver : drivers) {
            List<UsbSerialPort> ports = driver.getPorts();
            result.addAll(ports);
        }

        if (BuildConfig.DEBUG)
            Log.d(TAG, "Refreshing " + result.size() + " ports.");

        synchronized (mPorts) {
            mPorts.clear();
            mPorts.addAll(result);
        }

        if (mListener != null)
            mListener.onChanged();
    }

    private static List<UsbSerialDriver> getDummyDrivers() {
        List<UsbSerialDriver> list = new ArrayList<>();
        list.add(new Cp21xxSerialDriver(newUsbDevice("Test1", UsbId.VENDOR_SILABS, UsbId.SILABS_CP2102)));
        list.add(new CdcAcmSerialDriver(newUsbDevice("Test2", UsbId.VENDOR_ARDUINO, UsbId.ARDUINO_UNO)));
        return list;
    }

    private static UsbDevice newUsbDevice(String name, int vendorId, int productId) {
        try {
            return (UsbDevice) UsbDevice.class.getConstructors()[0].newInstance(
                    name, vendorId, productId,
                    0, 0, 0, null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public UsbSerialPort findPort(UsbDevice device, int portNumber) {
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        List<UsbSerialPort> ports = driver.getPorts();
        return ports.size() > portNumber ? ports.get(portNumber) : null;
    }

    public static String getName(UsbDevice device) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return device.getManufacturerName() + " " + device.getProductName();
        }

        int productId = device.getProductId();
        switch (device.getVendorId()) {
            case UsbId.VENDOR_SILABS:
                switch (productId) {
                    case UsbId.SILABS_CP2102:
                        return "Silicon Labs CP2102";
                    case UsbId.SILABS_CP2105:
                        return "Silicon Labs CP2105";
                    case UsbId.SILABS_CP2108:
                        return "Silicon Labs CP2108";
                    case UsbId.SILABS_CP2110:
                        return "Silicon Labs CP2110";
                    default:
                        break;
                }
                break;
            case UsbId.VENDOR_ARDUINO:
                switch (productId) {
                    case UsbId.ARDUINO_UNO:
                        return "Arduino Uno";
                    case UsbId.ARDUINO_UNO_R3:
                        return "Arduino Uno R3";
                    case UsbId.ARDUINO_MEGA_2560:
                        return "Arduino Mega 2560";
                    case UsbId.ARDUINO_MEGA_2560_R3:
                        return "Arduino Mega 2560 R3";
                    case UsbId.ARDUINO_MEGA_ADK:
                        return "Arduino Mega ADK";
                    case UsbId.ARDUINO_MEGA_ADK_R3:
                        return "Arduino Mega ADK R3";
                    case UsbId.ARDUINO_SERIAL_ADAPTER:
                        return "Arduino Serial Adapter";
                    case UsbId.ARDUINO_LEONARDO:
                        return "Arduino Leonardo";
                    default:
                        break;
                }
                break;
            case UsbId.VENDOR_FTDI:
                if (productId == UsbId.FTDI_FT232R) return "FTDI FT232R";
                else if (productId == UsbId.FTDI_FT231X) return "FTDI FT231X";
                break;
            case UsbId.VENDOR_PROLIFIC:
                if (productId == UsbId.PROLIFIC_PL2303) return "Prolific PL2303";
                break;
            default:
                break;
        }
        return null;
    }

    public interface Listener {
        void onChanged();
    }
}
