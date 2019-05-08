package xin.common.qqwry;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class IPLocation {
	
	private  byte[] data;
	
	private  long firstIndexOffset;
	
	private  long lastIndexOffset;
	
	private  long totalIndexCount;
	
	private static final byte REDIRECT_MODE_1 = 0x01;
	
	private static final byte REDIRECT_MODE_2 = 0x02;
	
	static   final long IP_RECORD_LENGTH = 7;
	
	private static ReentrantLock lock = new ReentrantLock();
	
	private static Long lastModifyTime = 0L;

	public static boolean enableFileWatch = false;
	
	private  File qqwryFile;
	
	public IPLocation(String  filePath) throws Exception {
		this.qqwryFile = new File(filePath);
		load();
		if(enableFileWatch){
			watch();
		}
	}

	private Runnable doWatch = () -> {
		long time = qqwryFile.lastModified();
		if (time > lastModifyTime) {
			lastModifyTime = time;
			try {
				load();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	};
	
    private void watch() {
    	Executors.newScheduledThreadPool(1).scheduleAtFixedRate(doWatch, 1000L, 5000L, TimeUnit.MILLISECONDS);
    }
    
	private void load() throws Exception {
    	log.info("loading data file ");
		lastModifyTime = qqwryFile.lastModified();
		ByteArrayOutputStream out = null;
		FileInputStream in = null;
		lock.lock();
		try {
			out = new ByteArrayOutputStream();
			byte[] b = new byte[1024];
			in = new FileInputStream(qqwryFile);
			while(in.read(b) != -1){
				out.write(b);
			}
			data = out.toByteArray();
			firstIndexOffset = read4ByteAsLong(0);
			lastIndexOffset = read4ByteAsLong(4);
			totalIndexCount = (lastIndexOffset - firstIndexOffset) / IP_RECORD_LENGTH + 1;
			in.close();
			out.close();
		} finally {
			try {
				if(out != null) {
					out.close();
				}
				if(in != null) {
					in.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			lock.unlock();
		}
	}
	
	private long read4ByteAsLong(final int  offset) {
		long val = data[offset] & 0xFF;
		val |= (data[offset + 1] << 8L) & 0xFF00L;
		val |= (data[offset + 2] << 16L) & 0xFF0000L;
		val |= (data[offset + 3] << 24L) & 0xFF000000L;
		return val;
	}

	private long read3ByteAsLong(final int offset) {
		long val = data[offset] & 0xFF;
		val |= (data[offset + 1] << 8) & 0xFF00;
		val |= (data[offset + 2] << 16) & 0xFF0000;
		return val;
	}
    
	private long search(long ip) {
		long low = 0;
		long high = totalIndexCount;
		long mid = 0;
		while(low <= high){
			mid = (low + high) >>> 1 ;
		    long indexIP = read4ByteAsLong((int)(firstIndexOffset + (mid - 1) * IP_RECORD_LENGTH));
	        long indexIPNext = read4ByteAsLong((int)(firstIndexOffset + mid * IP_RECORD_LENGTH));
		    if(indexIP <= ip && ip < indexIPNext) {
		    	return read3ByteAsLong((int)(firstIndexOffset + (mid - 1) * IP_RECORD_LENGTH + 4));
		    } else {
		    	if(ip > indexIP) {
				    low = mid + 1;
				} else if (ip < indexIP) {
				    high = mid - 1;
				}
		    }
		}
		return -1;
	}
	
	public Location fetchIPLocation(String ip) {
		long numericIp = inet_pton(ip);
		lock.lock();
		long offset = search(numericIp);
		try{
			if(offset != -1) {
				return readIPLocation((int)offset);
			}
		} finally {
		    lock.unlock();
		}
		return null;
	}
	
	private Location readIPLocation(final int offset) {
		final Location loc = new Location();
		try {
			byte redirectMode = data[offset + 4];
			if (redirectMode == REDIRECT_MODE_1) {
				long countryOffset = read3ByteAsLong(offset + 5);
				redirectMode = data[(int)countryOffset];
				if (redirectMode == REDIRECT_MODE_2) {
					final QqwryString region = readString((int)read3ByteAsLong((int)countryOffset + 1));
					loc.setRegion(region.string);
					countryOffset = countryOffset + 4;
				} else {
					final QqwryString region = readString((int)countryOffset);
					loc.setRegion(region.string);
					countryOffset += region.byteCountWithEnd;
				}
				loc.setOperator(readOperator((int)countryOffset));
			} else if (redirectMode == REDIRECT_MODE_2) {
				loc.setRegion(readString((int)read3ByteAsLong(offset + 5)).string);
				loc.setOperator(readOperator(offset + 8));
			} else {
				final QqwryString country = readString(offset + 4);
				loc.setRegion(country.string);
				loc.setOperator(readOperator(offset + 4 + country.byteCountWithEnd));
			}
			return loc;
		} catch (Exception e) {
			return null;
		}
	}

	private String readOperator(final int offset) {
		byte redirectMode = data[offset];
		if (redirectMode == REDIRECT_MODE_1 || redirectMode == REDIRECT_MODE_2) {
			long areaOffset = read3ByteAsLong(offset + 1);
			if (areaOffset == 0) {
				return "";
			} else {
				return readString((int)areaOffset).string;
			}
		} else {
			return readString(offset).string;
		}
	}
	
	private QqwryString readString(int offset) {
		int pos = offset;
		final byte[] b = new byte[128];
		int i;
		for (i = 0, b[i] = data[pos++]; b[i] != 0; b[++i] = data[pos++]);
		try{
			   return new QqwryString(new String(b,0,i,"GBK"),i + 1);
		} catch(UnsupportedEncodingException e) {
			return new QqwryString("",0);
		}
	}
	

	private static long inet_pton(String ipStr) {
		if(ipStr == null){
			throw new IllegalArgumentException("ip string could be null");
		}
		String [] arr = ipStr.split("\\.");
		long ip = (Long.parseLong(arr[0])  & 0xFFL) << 24 & 0xFF000000L;
		ip |=  (Long.parseLong(arr[1])  & 0xFFL) << 16 & 0xFF0000L;
		ip |=  (Long.parseLong(arr[2])  & 0xFFL) << 8 & 0xFF00L;
		ip |=  (Long.parseLong(arr[3])  & 0xFFL);
		return ip;
	}

	@ToString(of = "string")
	private class QqwryString{
		
		public final String string;

		public final int byteCountWithEnd;

		public QqwryString(final String string,final int byteCountWithEnd) {
			this.string = string;
			this.byteCountWithEnd = byteCountWithEnd;
		}

	}

	public static void main(String[] args) throws Exception {
		final IPLocation ipLocation = new IPLocation(IPLocation.class.getResource("/qqwry.dat").getPath());
		Location loc = ipLocation.fetchIPLocation("58.30.15.255");
		log.info("58.30.15.255 - {}",loc.toString());
		loc = ipLocation.fetchIPLocation("117.136.0.227");
		log.info("117.136.0.227 - {}",loc.toString());
		loc = ipLocation.fetchIPLocation("58.20.43.13");
		log.info("58.20.43.13 - {}",loc.toString());
	}
}

