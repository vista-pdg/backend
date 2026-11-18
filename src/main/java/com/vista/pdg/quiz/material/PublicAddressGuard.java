package com.vista.pdg.quiz.material;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Guardia contra SSRF: solo {@code http(s)} en los puertos estándar y hacia direcciones públicas.
 * Se aplica a cada salto de una redirección.
 */
public final class PublicAddressGuard {
  public static final String PRIVATE =
      "Ese enlace apunta a una dirección privada o local. Usa un enlace público.";

  private PublicAddressGuard() {}

  public static void check(URI uri, boolean allowPrivate) {
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) {
      throw new MaterialException("Usa un enlace que empiece por http:// o https://.");
    }
    if (uri.getHost() == null || uri.getHost().isBlank() || uri.getRawUserInfo() != null) {
      throw new MaterialException("El enlace no tiene un sitio válido.");
    }
    int port = uri.getPort();
    if (!allowPrivate && port != -1 && port != 80 && port != 443) {
      throw new MaterialException("Solo se admiten enlaces en los puertos web estándar.");
    }
    InetAddress[] addresses;
    try {
      addresses = InetAddress.getAllByName(uri.getHost());
    } catch (UnknownHostException e) {
      throw new MaterialException("No se encontró el sitio " + uri.getHost() + ".", e);
    }
    if (allowPrivate) return;
    for (InetAddress address : addresses) {
      if (blocked(address)) throw new MaterialException(PRIVATE);
    }
  }

  public static boolean blocked(InetAddress address) {
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) return true;
    byte[] b = address.getAddress();
    if (address instanceof Inet4Address) {
      int first = b[0] & 0xff;
      int second = b[1] & 0xff;
      return first == 0 // «esta red»
          || (first == 100 && second >= 64 && second <= 127) // NAT de operador
          || (first == 192 && second == 0 && (b[2] & 0xff) == 0) // asignaciones IETF
          || first >= 240; // reservada y difusión
    }
    if (address instanceof Inet6Address) {
      if ((b[0] & 0xfe) == 0xfc) return true; // direcciones locales únicas fc00::/7
      boolean mapped = true; // ::ffff:a.b.c.d lleva una IPv4 dentro
      for (int i = 0; i < 10; i++) mapped &= b[i] == 0;
      if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
        try {
          return blocked(InetAddress.getByAddress(new byte[] {b[12], b[13], b[14], b[15]}));
        } catch (UnknownHostException e) {
          return true;
        }
      }
    }
    return false;
  }
}
