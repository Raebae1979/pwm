/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.util;

import com.google.gson.*;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpProtocolParams;
import password.pwm.*;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.SmsItemBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.LocaleHelper;
import password.pwm.ldap.UserDataReader;
import password.pwm.util.intruder.RecordType;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.stats.Statistic;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A collection of static methods used throughout PWM
 *
 * @author Jason D. Rivard
 */
public class
        Helper {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(Helper.class);

    // -------------------------- STATIC METHODS --------------------------

    private Helper() {
    }


    public static String md5sum(final String input)
            throws IOException {
        if (input == null || input.length() < 1) {
            return null;
        }
        return md5sum(new ByteArrayInputStream(input.getBytes()));
    }

    public static String md5sum(final File theFile)
            throws IOException {
        return md5sum(new FileInputStream(theFile));
    }

    public static String md5sum(final InputStream is)
            throws IOException {
        return checksum(is, "MD5");
    }

    public static String checksum(final InputStream is, String algorithmName)
            throws IOException {

        final InputStream bis = is instanceof BufferedInputStream ? is : new BufferedInputStream(is);

        final MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance(algorithmName);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }

        final byte[] buffer = new byte[1024];
        int length;
        while (true) {
            length = bis.read(buffer, 0, buffer.length);
            if (length == -1) {
                break;
            }
            messageDigest.update(buffer, 0, length);
        }
        bis.close();

        final byte[] bytes = messageDigest.digest();

        return byteArrayToHexString(bytes);
    }

    /**
     * Convert a byte[] array to readable string format. This makes the "hex" readable
     *
     * @param in byte[] buffer to convert to string format
     * @return result String buffer in String format
     */
    public static String byteArrayToHexString(final byte in[]) {
        final String pseudo[] = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F"};

        if (in == null || in.length <= 0) {
            return "";
        }

        final StringBuilder out = new StringBuilder(in.length * 2);

        for (final byte b : in) {
            byte ch = (byte) (b & 0xF0);    // strip off high nibble
            ch = (byte) (ch >>> 4);         // shift the bits down
            ch = (byte) (ch & 0x0F);        // must do this is high order bit is on!
            out.append(pseudo[(int) ch]);   // convert the nibble to a String Character
            ch = (byte) (b & 0x0F);         // strip off low nibble
            out.append(pseudo[(int) ch]);   // convert the nibble to a String Character
        }

        return out.toString();
    }

    /**
     * Pause the calling thread the specified amount of time.
     *
     * @param sleepTimeMS - a time duration in milliseconds
     * @return time actually spent sleeping
     */
    public static long pause(final long sleepTimeMS) {
        final long startTime = System.currentTimeMillis();
        do {
            try {
                final long sleepTime = sleepTimeMS - (System.currentTimeMillis() - startTime);
                Thread.sleep(sleepTime > 0 ? sleepTime : 5);
            } catch (InterruptedException e) {
                //who cares
            }
        } while ((System.currentTimeMillis() - startTime) < sleepTimeMS);

        return System.currentTimeMillis() - startTime;
    }

    public static List<Integer> invokeExternalJudgeMethods(
            final Configuration config,
            //final PwmSession pwmSession,
            final String password)  {
        final List<String> externalMethods = config.readSettingAsStringArray(PwmSetting.EXTERNAL_JUDGE_METHODS);
        final List<Integer> returnList = new ArrayList<Integer>();

        // process any configured external change password methods configured.
        for (final String classNameString : externalMethods) {
            if (classNameString != null && classNameString.length() > 0) {
                try {
                    // load up the class and get an instance.
                    final Class<?> theClass = Class.forName(classNameString);
                    final ExternalJudgeMethod externalClass = (ExternalJudgeMethod) theClass.newInstance();

                    // invoke the passwordChange method;
                    final int result = externalClass.judgePassword(config, password);
                    LOGGER.trace("externalJudgeMethod '" + classNameString + "' returned a value of " + result);
                    returnList.add(result);
                } catch (ClassCastException e) {
                    LOGGER.error("configured external class " + classNameString + " is not an instance of " + ExternalChangeMethod.class.getName());
                } catch (ClassNotFoundException e) {
                    LOGGER.error("unable to load configured external class: " + classNameString + " " + e.getMessage() + "; perhaps the class is not in the classpath?");
                } catch (IllegalAccessException e) {
                    LOGGER.error("unable to load configured external class: " + classNameString + " " + e.getMessage());
                } catch (InstantiationException e) {
                    LOGGER.error("unable to load configured external class: " + classNameString + " " + e.getMessage());
                }
            }
        }

        return returnList;
    }

    public static boolean testEmailAddress(final String address) {
        final Pattern pattern = Pattern.compile(PwmConstants.EMAIL_REGEX_MATCH);
        final Matcher matcher = pattern.matcher(address);
        return matcher.matches();
    }

    public static boolean testUserMatchQueryString(
            final ChaiUser theUser,
            final String queryString
    )
            throws ChaiUnavailableException, PwmUnrecoverableException {
        if (theUser == null) {
            return false;
        }

        if (queryString == null || queryString.length() < 1) {
            return true;
        }

        try {
            final ChaiProvider provider = theUser.getChaiProvider();
            final String objectDN = theUser.getEntryDN();
            final Map<String, Map<String,String>> results = provider.search(objectDN, queryString, Collections.<String>emptySet(), ChaiProvider.SEARCH_SCOPE.SUBTREE);

            if (results == null || results.size() != 1) {
                return false;
            }

            final String returnedDN = Helper.trimString(results.keySet().iterator().next(), ",");

            if (returnedDN.equals(objectDN)) {
                return true;
            }
        } catch (ChaiOperationException e) {
            LOGGER.error("error testing match query string: " + queryString, e);
        }

        return false;
    }

    /**
     * Strips specified characters of the beginning and end of a string.  Similar to
     * {@link String#trim()}, except the caller can specify an arbitrary character instead
     * of just whitespace chars.
     *
     * @param str   String to operate on
     * @param chars A String containing characters to remove from beginning/end
     * @return the (possibly) modifed str value
     */
    public static String trimString(
            final String str,
            final String chars
    ) {
        if (chars == null || chars.length() < 1) {
            return str;
        }

        final StringBuilder sb = new StringBuilder(str);

        for (int i = 0; i < chars.length(); i++) {
            if (sb.charAt(0) == chars.charAt(i)) {
                sb.delete(0, 1);
            }

            if (sb.charAt(sb.length() - 1) == chars.charAt(i)) {
                sb.delete(sb.length() - 2, sb.length() - 1);
            }
        }

        return sb.toString();
    }




    /**
     * Writes a Map of form values to ldap onto the supplied user object.
     * The map key must be a string of attribute names.
     * <p/>
     * Any ldap operation exceptions are not reported (but logged).
     *
     * @param pwmSession       for looking up session info
     * @param theUser          User to write to
     * @param formValues       A map with {@link password.pwm.config.FormConfiguration} keys and String values.
     * @throws ChaiUnavailableException if the directory is unavailable
     * @throws PwmOperationalException if their is an unexpected ldap problem
     */
    public static void writeFormValuesToLdap(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final ChaiUser theUser,
            final Map<FormConfiguration,String> formValues,
            final boolean expandPwmMacros
    )
            throws ChaiUnavailableException, PwmOperationalException
    {
        final Map<String,String> tempMap = new HashMap<String,String>();

        for (final FormConfiguration formItem : formValues.keySet()) {
            if (!formItem.isReadonly()) {
                tempMap.put(formItem.getName(),formValues.get(formItem));
            }
        }

        writeMapToLdap(pwmApplication, theUser, tempMap, pwmSession.getUserInfoBean(), expandPwmMacros);
    }

    /**
     * Writes a Map of values to ldap onto the supplied user object.
     * The map key must be a string of attribute names.
     * <p/>
     * Any ldap operation exceptions are not reported (but logged).
     *
     * @param theUser          User to write to
     * @param valueMap       A map with String keys and String values.
     * @throws ChaiUnavailableException if the directory is unavailable
     * @throws PwmOperationalException if their is an unexpected ldap problem
     */
    public static void writeMapToLdap(
            final PwmApplication pwmApplication,
            final ChaiUser theUser,
            final Map<String,String> valueMap,
            final UserInfoBean userInfoBean,
            final boolean expandPwmMacros
    )
            throws PwmOperationalException, ChaiUnavailableException
    {
        final Map<String,String> currentValues;
        try {
            currentValues = theUser.readStringAttributes(valueMap.keySet());
        } catch (ChaiOperationException e) {
            final String errorMsg = "error reading existing values on user " + theUser.getEntryDN() + " prior to replacing values, error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            final PwmOperationalException newException = new PwmOperationalException(errorInformation);
            newException.initCause(e);
            throw newException;
        }

        for (final String attrName : valueMap.keySet()) {
            String attrValue = valueMap.get(attrName) != null ? valueMap.get(attrName) : "";
            if (expandPwmMacros) {
                final MacroMachine macroMachine = new MacroMachine(pwmApplication, userInfoBean, null);
                attrValue = macroMachine.expandMacros(attrValue);
            }
            if (!attrValue.equals(currentValues.get(attrName))) {
                if (attrValue.length() > 0) {
                    try {
                        theUser.writeStringAttribute(attrName, attrValue);
                        LOGGER.info("set attribute on user " + theUser.getEntryDN() + " (" + attrName + "=" + attrValue + ")");
                    } catch (ChaiOperationException e) {
                        final String errorMsg = "error setting '" + attrName + "' attribute on user " + theUser.getEntryDN() + ", error: " + e.getMessage();
                        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                        final PwmOperationalException newException = new PwmOperationalException(errorInformation);
                        newException.initCause(e);
                        throw newException;
                    }
                } else {
                    if (currentValues.get(attrName) != null && currentValues.get(attrName).length() > 0) {
                        try {
                            theUser.deleteAttribute(attrName, null);
                            LOGGER.info("deleted attribute value on user " + theUser.getEntryDN() + " (" + attrName + ")");
                        } catch (ChaiOperationException e) {
                            final String errorMsg = "error removing '" + attrName + "' attribute value on user " + theUser.getEntryDN() + ", error: " + e.getMessage();
                            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                            final PwmOperationalException newException = new PwmOperationalException(errorInformation);
                            newException.initCause(e);
                            throw newException;
                        }
                    }
                }
            } else {
                LOGGER.debug("skipping attribute modify for attribute '" + attrName + "', no change in value");
            }
        }
    }

    public static String binaryArrayToHex(final byte[] buf) {
        final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();
        final char[] chars = new char[2 * buf.length];
        for (int i = 0; i < buf.length; ++i) {
            chars[2 * i] = HEX_CHARS[(buf[i] & 0xF0) >>> 4];
            chars[2 * i + 1] = HEX_CHARS[buf[i] & 0x0F];
        }
        return new String(chars);
    }

    public static long getFileDirectorySize(final File dir) {
        long size = 0;
        try {
            if (dir.isFile()) {
                size = dir.length();
            } else {
                final File[] subFiles = dir.listFiles();

                for (final File file : subFiles) {
                    if (file.isFile()) {
                        size += file.length();
                    } else {
                        size += getFileDirectorySize(file);
                    }

                }
            }
        } catch (NullPointerException e) {
            // file was deleted before file size could be read
        }

        return size;
    }

    public static String formatDiskSize(final long diskSize) {
        final float COUNT = 1000;
        if (diskSize < 1) {
            return "n/a";
        }

        if (diskSize == 0) {
            return "0";
        }

        final NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(2);

        if (diskSize > COUNT * COUNT * COUNT) {
            final StringBuilder sb = new StringBuilder();
            sb.append(nf.format(diskSize / COUNT / COUNT / COUNT));
            sb.append(" GB");
            return sb.toString();
        }

        if (diskSize > COUNT * COUNT) {
            final StringBuilder sb = new StringBuilder();
            sb.append(nf.format(diskSize / COUNT / COUNT));
            sb.append(" MB");
            return sb.toString();
        }

        return NumberFormat.getInstance().format(diskSize) + " bytes";
    }


    public static File figureFilepath(final String filename, final File suggestedPath)
    {
        if (filename == null || filename.length() < 1) {
            return null;
        }

        if ((new File(filename)).isAbsolute()) {
            return new File(filename);
        }

        return new File(suggestedPath + File.separator + filename);
    }

    public static Boolean fileExists(final String filename) {
        if (filename != null) {
            File file = new File(filename);
            return file.exists() && file.isFile();
        }
        return false;
    }

    public static Boolean directoryExists(final String dirname) {
        if (dirname != null) {
            File directory = new File(dirname);
            return directory.exists() && directory.isDirectory();
        }
        return false;
    }

    public static String readFileAsString(final File filePath, final long maxLength, final String charset)
            throws IOException {
        final StringBuilder fileData = new StringBuilder();

        final BufferedReader reader = new BufferedReader(
                charset == null ?
                        new InputStreamReader(new FileInputStream(filePath)) :
                        new InputStreamReader(new FileInputStream(filePath), "UTF8"));

        char[] buf = new char[1024];
        int numRead;
        int charsRead = 0;
        while ((numRead = reader.read(buf)) != -1 && (charsRead < maxLength)) {
            final String readData = String.valueOf(buf, 0, numRead);
            fileData.append(readData);
            buf = new char[1024];
            charsRead += numRead;
        }
        reader.close();
        return fileData.toString();
    }

    public static void writeFileAsString(final File filePath, final String output, final String charset)
            throws IOException {
        final OutputStreamWriter osw =
                charset == null ?
                        new OutputStreamWriter(new FileOutputStream(filePath, false)) :
                        new OutputStreamWriter(new FileOutputStream(filePath, false), charset);

        osw.write(output);
        osw.flush();
        osw.close();
    }

    public static String replaceAllPatterns(final String input, final Properties fields) {
        String output = input;
        Enumeration names = fields.propertyNames();
        while (names.hasMoreElements()) {
            final String key = (String) names.nextElement();
            final String fieldName = "%"+key+"%";
            final String fieldValue = fields.getProperty(key);
            output = output.replaceAll(fieldName, fieldValue);
        }
        return output;
    }

    public static long diskSpaceRemaining(final File file) {
        try {
            final Method getFreeSpaceMethod = File.class.getMethod("getFreeSpace");
            final Object rawResult = getFreeSpaceMethod.invoke(file);
            return (Long) rawResult;
        } catch (NoSuchMethodException e) {
            /* no error, pre java 1.6 doesn't have this method */
        } catch (Exception e) {
            LOGGER.debug("error reading file space remaining for " + file.toString() + ",: " + e.getMessage());
        }
        return -1;
    }

    public static HttpClient getHttpClient(final Configuration configuration)
    {
        final DefaultHttpClient httpClient = new DefaultHttpClient();

        final String strValue = configuration.readSettingAsString(PwmSetting.HTTP_PROXY_URL);
        if (strValue != null && strValue.length() > 0) {
            final URI proxyURI = URI.create(strValue);

            final String host = proxyURI.getHost();
            final int port = proxyURI.getPort();
            final HttpHost proxy = new HttpHost(host,port);
            httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);

            final String username = proxyURI.getUserInfo();
            if (username != null && username.length() > 0) {
                final String password = (username.contains(":")) ? username.split(":")[1] : "";
                final UsernamePasswordCredentials passwordCredentials = new UsernamePasswordCredentials(username,password);
                httpClient.getCredentialsProvider().setCredentials (new AuthScope(host, port),passwordCredentials);
            }
        }
        final String userAgent = PwmConstants.PWM_APP_NAME + " " + PwmConstants.SERVLET_VERSION;
        httpClient.getParams().setParameter(HttpProtocolParams.USER_AGENT, userAgent);
        return httpClient;
    }

    static public String buildPwmFormID(final SessionStateBean ssBean) {
        return ssBean.getSessionVerificationKey() + Long.toString(ssBean.getRequestCounter(),36);
    }

    public static String resolveStringKeyLocaleMap(Locale desiredLocale, final Map<String,String> inputMap) {
        if (inputMap == null || inputMap.isEmpty()) {
            return null;
        }

        if (desiredLocale == null) {
            desiredLocale = PwmConstants.DEFAULT_LOCALE;
        }

        final Map<Locale,String> localeMap = new LinkedHashMap<Locale, String>();
        for (final String localeStringKey : inputMap.keySet()) {
            localeMap.put(LocaleHelper.parseLocaleString(localeStringKey),inputMap.get(localeStringKey));
        }

        final Locale selectedLocale = localeResolver(desiredLocale, localeMap.keySet());
        return localeMap.get(selectedLocale);
    }

    public static Locale localeResolver(final Locale desiredLocale, final Collection<Locale> localePool) {
        if (desiredLocale == null || localePool == null || localePool.isEmpty()) {
            return null;
        }

        for (final Locale loopLocale : localePool) {
            if (loopLocale.getLanguage().equalsIgnoreCase(desiredLocale.getLanguage())) {
                if (loopLocale.getCountry().equalsIgnoreCase(desiredLocale.getCountry())) {
                    if (loopLocale.getVariant().equalsIgnoreCase(desiredLocale.getVariant())) {
                        return loopLocale;
                    }
                }
            }
        }

        for (final Locale loopLocale : localePool) {
            if (loopLocale.getLanguage().equalsIgnoreCase(desiredLocale.getLanguage())) {
                if (loopLocale.getCountry().equalsIgnoreCase(desiredLocale.getCountry())) {
                    return loopLocale;
                }
            }
        }

        for (final Locale loopLocale : localePool) {
            if (loopLocale.getLanguage().equalsIgnoreCase(desiredLocale.getLanguage())) {
                return loopLocale;
            }
        }

        if (localePool.contains(PwmConstants.DEFAULT_LOCALE)) {
            return PwmConstants.DEFAULT_LOCALE;
        }

        if (localePool.contains(new Locale(""))) {
            return new Locale("");
        }

        return null;
    }

    public static void rotateBackups(final File inputFile, final int maxRotate) {
        if (maxRotate < 1) {
            return;
        }
        for (int i = maxRotate; i >= 0; i--) {
            final File thisFile = (i == 0) ? inputFile : new File(inputFile.getAbsolutePath() + "-" + i);
            final File youngerFile = (i <= 1) ? inputFile : new File(inputFile.getAbsolutePath() + "-" + (i - 1));

            if (i == maxRotate) {
                if (thisFile.exists()) {
                    LOGGER.debug("deleting old backup file: " + thisFile.getAbsolutePath());
                    if (!thisFile.delete()) {
                        LOGGER.error("unable to delete old backup file: " + thisFile.getAbsolutePath());
                    }
                }
            } else if (i == 0 || youngerFile.exists()) {
                final File destFile = new File(inputFile.getAbsolutePath() + "-" + (i + 1));
                LOGGER.debug("backup file " + thisFile.getAbsolutePath() + " renamed to " + destFile.getAbsolutePath());
                if (!thisFile.renameTo(destFile)) {
                    LOGGER.debug("unable to rename file " + thisFile.getAbsolutePath() + " to " + destFile.getAbsolutePath());
                }
            }
        }
    }

    public static Date nextZuluZeroTime() {
        final Calendar nextZuluMidnight = GregorianCalendar.getInstance(TimeZone.getTimeZone("Zulu"));
        nextZuluMidnight.set(Calendar.HOUR_OF_DAY,0);
        nextZuluMidnight.set(Calendar.MINUTE,0);
        nextZuluMidnight.set(Calendar.SECOND, 0);
        nextZuluMidnight.add(Calendar.HOUR, 24);
        return nextZuluMidnight.getTime();
    }

    public static class SimpleTextCrypto {

        public static String encryptValue(final String value, final SecretKey key)
                throws PwmUnrecoverableException
        {
            return encryptValue(value, key, false);
        }

        public static String encryptValue(final String value, final SecretKey key, final boolean urlSafe)
                throws PwmUnrecoverableException
        {
            try {
                if (value == null || value.length() < 1) {
                    return "";
                }

                final Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.ENCRYPT_MODE, key, cipher.getParameters());
                final byte[] encrypted = cipher.doFinal(value.getBytes());
                return urlSafe ?  Base64Util.encodeBytes(encrypted, Base64Util.URL_SAFE | Base64Util.GZIP) : Base64Util.encodeBytes(encrypted);
            } catch (Exception e) {
                final String errorMsg = "unexpected error performing simple crypt operation: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                LOGGER.error(errorInformation.toDebugStr());
                throw new PwmUnrecoverableException(errorInformation);
            }
        }

        public static String decryptValue(final String value, final SecretKey key)
                throws PwmUnrecoverableException
        {
            return decryptValue(value, key, false);
        }

        public static String decryptValue(final String value, final SecretKey key, final boolean urlSafe)
                throws PwmUnrecoverableException
        {
            try {
                if (value == null || value.length() < 1) {
                    return "";
                }

                final byte[] decoded = urlSafe ? Base64Util.decode(value, Base64Util.URL_SAFE | Base64Util.GZIP): Base64Util.decode(value);
                final Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.DECRYPT_MODE, key);
                final byte[] decrypted = cipher.doFinal(decoded);
                return new String(decrypted);
            } catch (Exception e) {
                final String errorMsg = "unexpected error performing simple decrypt operation: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                throw new PwmUnrecoverableException(errorInformation);
            }
        }

        public static SecretKey makeKey(final String text)
                throws NoSuchAlgorithmException, UnsupportedEncodingException {
            final MessageDigest md = MessageDigest.getInstance("SHA1");
            md.update(text.getBytes("iso-8859-1"), 0, text.length());
            final byte[] key = new byte[16];
            System.arraycopy(md.digest(), 0, key, 0, 16);
            return new SecretKeySpec(key, "AES");
        }
    }

    public static String figureForwardURL(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpServletRequest req
    ) {
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        String redirectURL = ssBean.getForwardURL();
        if (redirectURL == null || redirectURL.length() < 1) {
            redirectURL = pwmApplication.getConfig().readSettingAsString(PwmSetting.URL_FORWARD);
        }

        if (redirectURL == null || redirectURL.length() < 1) {
            redirectURL = req.getContextPath();
        }

        return redirectURL;
    }

    public static String figureLogoutURL(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    ) {
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        return ssBean.getLogoutURL() == null ? pwmApplication.getConfig().readSettingAsString(PwmSetting.URL_LOGOUT) : ssBean.getLogoutURL();
    }

    public static int figureLdapConnectionCount(final PwmApplication pwmApplication, final ContextManager contextManager) {
        int counter = 0;
        try {
            for (final String identifer : pwmApplication.getConfig().getLdapProfiles().keySet()) {
                if (pwmApplication.getProxyChaiProvider(identifer).isConnected()) {
                    counter++;
                }
            }

            for (final PwmSession loopSession : contextManager.getPwmSessions()) {
                if (loopSession != null) {
                    if (loopSession.getSessionManager().hasActiveLdapConnection()) {
                        counter++;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("unexpected error counting ldap connections: " + e.getMessage());
        }
        return counter;
    }

    public static HashMap splitStringToMap(final String input) {
        HashMap result = new HashMap();
        if (input != null) {
            final List<String> items = Arrays.asList(input.split(";"));
            for (final Iterator<String> it = items.iterator(); it.hasNext(); ) {
                String item = it.next();
                String[] parts = item.split(":", 2);
                if (parts.length > 0) {
                    String left = parts[0];
                    String right = null;
                    if (parts.length == 2) {
                        right = parts[1];
                    }
                    result.put(left, right);
                }
            }
        }
        return result;
    }

    public static class TokenSender {
        public static void sendToken(
                final PwmApplication pwmApplication,
                final UserInfoBean userInfoBean,
                final UserDataReader userDataReader,
                final EmailItemBean configuredEmailSetting,
                final MessageSendMethod tokenSendMethod,
                final String emailAddress,
                final String smsNumber,
                final String smsMessage,
                final String tokenKey
        )
                throws PwmUnrecoverableException, ChaiUnavailableException
        {
            final boolean success;
            switch (tokenSendMethod) {
                case NONE:
                    // should never get here
                    LOGGER.error("attempt to send token to destination type 'NONE'");
                    throw new PwmUnrecoverableException(PwmError.ERROR_UNKNOWN);
                case BOTH:
                    // Send both email and SMS, success if one of both succeeds
                    final boolean suc1 = sendEmailToken(pwmApplication, userInfoBean, userDataReader, configuredEmailSetting, emailAddress, tokenKey);
                    final boolean suc2 = sendSmsToken(pwmApplication, userInfoBean, userDataReader, smsNumber, smsMessage, tokenKey);
                    success = suc1 || suc2;
                    break;
                case EMAILFIRST:
                    // Send email first, try SMS if email is not available
                    success = sendEmailToken(pwmApplication, userInfoBean, userDataReader, configuredEmailSetting, emailAddress, tokenKey) ||
                            sendSmsToken(pwmApplication, userInfoBean, userDataReader, smsNumber, smsMessage, tokenKey);
                    break;
                case SMSFIRST:
                    // Send SMS first, try email if SMS is not available
                    success = sendSmsToken(pwmApplication, userInfoBean, userDataReader, smsNumber, smsMessage, tokenKey) ||
                            sendEmailToken(pwmApplication, userInfoBean, userDataReader, configuredEmailSetting, emailAddress, tokenKey);
                    break;
                case SMSONLY:
                    // Only try SMS
                    success = sendSmsToken(pwmApplication, userInfoBean, userDataReader, smsNumber, smsMessage, tokenKey);
                    break;
                case EMAILONLY:
                default:
                    // Only try email
                    success = sendEmailToken(pwmApplication, userInfoBean, userDataReader, configuredEmailSetting, emailAddress, tokenKey);
                    break;
            }
            if (!success) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_TOKEN_MISSING_CONTACT));
            }
            pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_TOKENS_SENT);
        }

        public static boolean sendEmailToken(
                final PwmApplication pwmApplication,
                final UserInfoBean userInfoBean,
                final UserDataReader userDataReader,
                final EmailItemBean configuredEmailSetting,
                final String toAddress,
                final String tokenKey
        )
                throws PwmUnrecoverableException, ChaiUnavailableException
        {
            if (toAddress == null || toAddress.length() < 1) {
                return false;
            }

            pwmApplication.getIntruderManager().mark(RecordType.TOKEN_DEST, toAddress, null);

            pwmApplication.getEmailQueue().submitEmail(new EmailItemBean(
                    toAddress,
                    configuredEmailSetting.getFrom(),
                    configuredEmailSetting.getSubject(),
                    configuredEmailSetting.getBodyPlain().replace("%TOKEN%", tokenKey),
                    configuredEmailSetting.getBodyHtml().replace("%TOKEN%", tokenKey)
            ), userInfoBean, userDataReader);
            LOGGER.debug("token email added to send queue for " + toAddress);
            return true;
        }

        public static boolean sendSmsToken(
                final PwmApplication pwmApplication,
                final UserInfoBean userInfoBean,
                final UserDataReader userDataReader,
                final String smsNumber,
                final String smsMessage,
                final String tokenKey
        )
                throws PwmUnrecoverableException, ChaiUnavailableException
        {
            final Configuration config = pwmApplication.getConfig();
            String senderId = config.readSettingAsString(PwmSetting.SMS_SENDER_ID);
            if (senderId == null) { senderId = ""; }

            if (smsNumber == null || smsNumber.length() < 1) {
                return false;
            }

            final String modifiedMessage = smsMessage.replaceAll("%TOKEN%", tokenKey);

            pwmApplication.getIntruderManager().mark(RecordType.TOKEN_DEST, smsNumber, null);

            final Integer maxlen = ((Long) config.readSettingAsLong(PwmSetting.SMS_MAX_TEXT_LENGTH)).intValue();
            pwmApplication.sendSmsUsingQueue(new SmsItemBean(smsNumber, senderId, modifiedMessage, maxlen), userInfoBean, userDataReader);
            LOGGER.debug("token SMS added to send queue for " + smsNumber);
            return true;
        }
    }

    public static Gson getGson(GsonBuilder gsonBuilder) {
        if (gsonBuilder == null) {
            gsonBuilder = new GsonBuilder();
        }
        return gsonBuilder
                .registerTypeAdapter(Date.class, new DateTypeAdapter())
                .registerTypeAdapter(X509Certificate.class, new X509CertificateAdapter())
                .create();
    }

    public static Gson getGson() {
        return GSON_SINGLETON;
    }

    private static Gson GSON_SINGLETON = new GsonBuilder()
            .registerTypeAdapter(Date.class, new DateTypeAdapter())
            .registerTypeAdapter(X509Certificate.class, new X509CertificateAdapter())
            .create();

    /**
     * Gson Serializer for {@link X509Certificate}.  Neccessary because sometimes X509Certs have circular refecences
     * and the default gson serializer will cause a {@code java.lang.StackOverflowError}.  Standard Base64 encoding of
     * the cert is used as the json format.
     */
    private static class X509CertificateAdapter implements JsonSerializer<X509Certificate>, JsonDeserializer<X509Certificate> {
        private X509CertificateAdapter() {
        }

        public synchronized JsonElement serialize(X509Certificate cert, Type type, JsonSerializationContext jsonSerializationContext) {
            try {
                return new JsonPrimitive(Base64Util.encodeBytes(cert.getEncoded()));
            } catch (CertificateEncodingException e) {
                throw new IllegalStateException("unable to json-encode certificate: " + e.getMessage());
            }
        }

        public X509Certificate deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext)
                throws JsonParseException
        {
            try {
                final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                return (X509Certificate)certificateFactory.generateCertificate(new ByteArrayInputStream(Base64Util.decode(jsonElement.getAsString())));
            } catch (Exception e) {
                throw new JsonParseException("unable to parse x509certificate: " + e.getMessage());
            }
        }
    }

    /**
     * GsonSerializer that stores dates in ISO 8601 format, with a deserialier that also reads local-platform format reading.
     */
    private static class DateTypeAdapter implements JsonSerializer<Date>, JsonDeserializer<Date> {
        private static final DateFormat isoDateFormat;
        private static final DateFormat gsonDateFormat;

        static {
            isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            isoDateFormat.setTimeZone(TimeZone.getTimeZone("Zulu"));

            gsonDateFormat = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT);
            gsonDateFormat.setTimeZone(TimeZone.getDefault());
        }

        private DateTypeAdapter() {
        }

        public synchronized JsonElement serialize(Date date, Type type, JsonSerializationContext jsonSerializationContext) {
            return new JsonPrimitive(isoDateFormat.format(date));
        }

        public synchronized Date deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) {
            try {
                return isoDateFormat.parse(jsonElement.getAsString());
            } catch (ParseException e) { /* noop */ }

            // for backwards compatibility
            try {
                return gsonDateFormat.parse(jsonElement.getAsString());
            } catch (ParseException e) {
                LOGGER.error("unable to parse stored json timestamp '" + jsonElement.getAsString() + "' error: " + e.getMessage());
                throw new JsonParseException(e);
            }
        }
    }

    public static String makeThreadName(final PwmApplication pwmApplication, final Class theClass) {
        String instanceName = "-";
        if (pwmApplication != null && pwmApplication.getInstanceID() != null) {
            instanceName = pwmApplication.getInstanceID();
        }

        return PwmConstants.PWM_APP_NAME + "-" + instanceName + "-" + theClass.getSimpleName();
    }
}
