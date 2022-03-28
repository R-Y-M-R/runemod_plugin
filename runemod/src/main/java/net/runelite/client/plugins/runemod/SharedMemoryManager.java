package net.runelite.client.plugins.runemod;


import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.W32APIOptions;

import static com.sun.jna.platform.win32.WinBase.INVALID_HANDLE_VALUE;
import static com.sun.jna.platform.win32.WinNT.PAGE_READWRITE;

public class SharedMemoryManager
{
	private final MyKernel32 myKernel32;
	private WinNT.HANDLE SharedMemoryMutex;
	public Pointer SharedMemoryData;
	private WinNT.HANDLE SharedMemoryHandle;
	int SharedMemorySize = 0;

	public interface MyKernel32 extends Kernel32
	{
		MyKernel32 INSTANCE = (MyKernel32) Native.loadLibrary("kernel32", MyKernel32.class, W32APIOptions.DEFAULT_OPTIONS);
		HANDLE OpenFileMapping(int dwDesiredAccess, boolean bInheritHandle, String lpName);

	}

	public SharedMemoryManager() {
		myKernel32 = MyKernel32.INSTANCE;
	}


/*
	public static void main (String[] args)

	{
		ACCSharedMemory accSharedMemory = new ACCSharedMemory();
		accSharedMemory.test();
	}
*/

	public void test(int[] clientUiPixels)
	{
		String SharedMemoryName = "sharedmem";
		SharedMemorySize = clientUiPixels.length*4;

		SharedMemoryHandle = myKernel32.CreateFileMapping(INVALID_HANDLE_VALUE,
			null, PAGE_READWRITE,
			0,
			SharedMemorySize,
			SharedMemoryName);

		if (SharedMemoryHandle == null) return;

		SharedMemoryData = myKernel32.MapViewOfFile(SharedMemoryHandle,
			0x001f,
			0, 0,
			SharedMemorySize);

		if (SharedMemoryData == null) return;

		byte[] buf = new byte[SharedMemorySize];
		buf[0] = 127;
		buf[1] = 127;
		buf[2] = 1;
		buf[3] = 1;
		SharedMemoryData.write(0,buf,0,SharedMemorySize);
		System.out.println(SharedMemoryData.getByte(1));
		//myKernel32.UnmapViewOfFile(SharedMemoryData);
		//myKernel32.CloseHandle(SharedMemoryHandle);

/*		WinNT.HANDLE SharedMemoryHandle1 = myKernel32.OpenFileMapping(0x001f, true, "sharedmem");
		if (SharedMemoryHandle1 == null)
		{
			System.out.println("h null");
		}*/
	}

	public void setInt_BigEndian(int offset, int anInt)
	{
		SharedMemoryData.setByte(0 + offset, (byte)(anInt >> 24));
		SharedMemoryData.setByte(1 + offset, (byte)(anInt >> 16));
		SharedMemoryData.setByte(2 + offset, (byte)(anInt >> 8));
		SharedMemoryData.setByte(3 + offset, (byte)(anInt));
	}

	public void setDataLength(int length) {
		setInt_BigEndian(0, length);
	}

	public void createSharedMemory(String SharedMemoryName, int SharedMemorySize)
	{
		SharedMemoryHandle = myKernel32.CreateFileMapping(INVALID_HANDLE_VALUE,
			null, PAGE_READWRITE,
			0,
			SharedMemorySize,
			SharedMemoryName);

		if (SharedMemoryHandle == null) return;

		SharedMemoryData = myKernel32.MapViewOfFile(SharedMemoryHandle,
			0x001f,
			0, 0,
			SharedMemorySize);

		if (SharedMemoryData == null) return;

		SharedMemoryData.setInt(0, SharedMemorySize); //first 4 bytes contain sharedMemoryDataSize
	}

	public void testPart2(int[] clientUiPixels)
	{
		SharedMemoryData.write(0,clientUiPixels,0,SharedMemorySize/4);
		//System.out.println(SharedMemoryData.getByte(1));
	}



	public void CloseSharedMemory()
	{
		if (SharedMemoryMutex != null)
		{
			//myKernel32.ReleaseMutex(SharedMemoryMutex);
			myKernel32.CloseHandle(SharedMemoryMutex);
			SharedMemoryMutex = null;
		}

		if (SharedMemoryData != null)
		{
			myKernel32.UnmapViewOfFile(SharedMemoryData);
			SharedMemoryData = null;
		}

		if (SharedMemoryHandle != null)
		{
			myKernel32.CloseHandle(SharedMemoryHandle);
			SharedMemoryHandle = null;
		}
	}

}