package com.senzing.runner;

/**
 * Identifies the various types of operating systems.
 */
public enum OperatingSystemFamily {
    /**
     * Microsoft Windows operating systems.
     */
    WINDOWS,
  
    /**
     * Apple Macintosh operating systems.
     */
    MAC_OS,
  
    /**
     * Unix, Linux and Linux-like operating systems.
     */
    UNIX;
  
    /**
     * Check to see if this is {@link #WINDOWS}.
     *
     * @return <code>true</code> if windows, otherwise <code>false</code>.
     */
    public boolean isWindows() {
      return (this == WINDOWS);
    }
  
    /**
     * Check to see if this is {@link #MAC_OS}.
     *
     * @return <code>true</code> if macOS, otherwise <code>false</code>.
     */
    public boolean isMacOS() {
      return (this == MAC_OS);
    }
  
    /**
     * Check to see if this is {@link #UNIX}.
     *
     * @return <code>true</code> if Unix, otherwise <code>false</code>.
     */
    public boolean isUnix() {
      return (this == UNIX);
    }
  
    /**
     * The {@link OperatingSystemFamily} on which the process is currently
     * executing.
     */
    public static final OperatingSystemFamily RUNTIME_OS_FAMILY;
  
    static {
      try {
        OperatingSystemFamily osFamily = null;
  
        final String osName = System.getProperty("os.name");
        String lowerOSName = osName.toLowerCase().trim();
        if (lowerOSName.startsWith("windows")) {
          osFamily = WINDOWS;
        } else if (lowerOSName.startsWith("mac")
            || lowerOSName.indexOf("darwin") >= 0) {
          osFamily = MAC_OS;
        } else {
          osFamily = UNIX;
        }
  
        RUNTIME_OS_FAMILY = osFamily;
      } catch (Exception e) {
        e.printStackTrace();
        throw new ExceptionInInitializerError(e);
      }
    }
  }
  