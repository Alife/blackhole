package us.codecraft.blackhole.cache;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xbill.DNS.Message;
import org.xbill.DNS.Section;

import us.codecraft.blackhole.config.Configure;
import us.codecraft.blackhole.utils.RecordUtils;

/**
 * @author yihua.huang@dianping.com
 * @date Dec 19, 2012
 */
@Component
public class CacheManager implements InitializingBean {

	@Autowired
	private Configure configure;

	private volatile ExecutorService cacheSaveExecutors;

	private Logger logger = Logger.getLogger(getClass());

	@Autowired
	private CacheClient cacheClient;

	public byte[] getFromCache(Message query) {
		if (!configure.isUseCache()) {
			return null;
		}
		UDPPackage udpPackage = cacheClient
				.<UDPPackage> get(getCacheKey(query));
		if (udpPackage == null) {
			return null;
		}
		byte[] bytes = udpPackage.getBytes(query.getHeader().getID());
		return bytes;
	}

	public <T> boolean set(Message query, T value, int expireTime) {
		return cacheClient.set(getCacheKey(query), value, expireTime);
	}

	public <T> T get(Message query) {
		return cacheClient.get(getCacheKey(query));
	}

	public <T> boolean set(String key, T value, int expireTime) {
		return cacheClient.set(key, value, expireTime);
	}

	public <T> T get(String key) {
		return cacheClient.get(key);
	}

	public String getCacheKey(Message query) {
		return RecordUtils.recordKey(query.getQuestion());
	}

	private int minTTL(Message response) {
		return (int) Math.min(RecordUtils.maxTTL(response
				.getSectionArray(Section.ANSWER)), RecordUtils.maxTTL(response
				.getSectionArray(Section.ADDITIONAL)));
	}

	public void setToCache(final Message query, final byte[] responseBytes) {
		if (configure.isUseCache()) {
			getCacheSaveExecutors().execute(new Runnable() {

				@Override
				public void run() {
					try {
						Message response = new Message(responseBytes);
						cacheClient.set(getCacheKey(query), new UDPPackage(
								responseBytes), minTTL(response));
					} catch (Throwable e) {
						logger.warn("set to cache error ", e);
					}

				}
			});
		}

	}

	/**
	 * @return the cacheSaveExecutors
	 */
	private ExecutorService getCacheSaveExecutors() {
		if (cacheSaveExecutors == null) {
			synchronized (this) {
				cacheSaveExecutors = Executors.newFixedThreadPool(50);
			}
		}
		return cacheSaveExecutors;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
	 */
	@Override
	public void afterPropertiesSet() throws Exception {
		if (configure.isUseCache()) {
			getCacheSaveExecutors();
		}
	}

}
