/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: /usr/local/lib/android/sdk/build-tools/36.0.0/aidl -p/usr/local/lib/android/sdk/platforms/android-37.0/framework.aidl -o/home/runner/work/Smartspacer_Alarms/Smartspacer_Alarms/app/build/generated/aidl_source_output_dir/debug/out -I/home/runner/work/Smartspacer_Alarms/Smartspacer_Alarms/app/src/main/aidl -I/home/runner/work/Smartspacer_Alarms/Smartspacer_Alarms/app/src/debug/aidl -I/home/runner/.gradle/caches/9.3.1/transforms/4d6bb415346ea5ac8d6df87f40f75433/transformed/core-1.15.0/aidl -I/home/runner/.gradle/caches/9.3.1/transforms/ff00f4fa874a38a7cc559124539576a5/transformed/sdk-core-1.1.2/aidl -I/home/runner/.gradle/caches/9.3.1/transforms/b75c9d004ae60e0bc46b68f544198921/transformed/versionedparcelable-1.1.1/aidl -d/tmp/aidl7472878811941498291.d /home/runner/work/Smartspacer_Alarms/Smartspacer_Alarms/app/src/main/aidl/com/dakomi/smartspacer/alarms/service/IAlarmReaderService.aidl
 *
 * DO NOT CHECK THIS FILE INTO A CODE TREE (e.g. git, etc..).
 * ALWAYS GENERATE THIS FILE FROM UPDATED AIDL COMPILER
 * AS A BUILD INTERMEDIATE ONLY. THIS IS NOT SOURCE CODE.
 */
package com.dakomi.smartspacer.alarms.service;
public interface IAlarmReaderService extends android.os.IInterface
{
  /** Default implementation for IAlarmReaderService. */
  public static class Default implements com.dakomi.smartspacer.alarms.service.IAlarmReaderService
  {
    @Override public java.lang.String getDumpsysAlarm() throws android.os.RemoteException
    {
      return null;
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.dakomi.smartspacer.alarms.service.IAlarmReaderService
  {
    /** Construct the stub and attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.dakomi.smartspacer.alarms.service.IAlarmReaderService interface,
     * generating a proxy if needed.
     */
    public static com.dakomi.smartspacer.alarms.service.IAlarmReaderService asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.dakomi.smartspacer.alarms.service.IAlarmReaderService))) {
        return ((com.dakomi.smartspacer.alarms.service.IAlarmReaderService)iin);
      }
      return new com.dakomi.smartspacer.alarms.service.IAlarmReaderService.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      if (code == INTERFACE_TRANSACTION) {
        reply.writeString(descriptor);
        return true;
      }
      switch (code)
      {
        case TRANSACTION_getDumpsysAlarm:
        {
          java.lang.String _result = this.getDumpsysAlarm();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.dakomi.smartspacer.alarms.service.IAlarmReaderService
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      @Override public java.lang.String getDumpsysAlarm() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getDumpsysAlarm, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
    }
    static final int TRANSACTION_getDumpsysAlarm = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "com.dakomi.smartspacer.alarms.service.IAlarmReaderService";
  public java.lang.String getDumpsysAlarm() throws android.os.RemoteException;
}
