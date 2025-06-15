package sbp.transfer.security.jaas;

import java.io.Serializable;
import java.security.Principal;
import java.util.Objects;

public class RolePrincipal implements Principal, Serializable {

  private static final long serialVersionUID = 1L;
  private final String name;

  public RolePrincipal(String name) {
    if (name == null) {
      throw new NullPointerException("Role name cannot be null.");
    }
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RolePrincipal that = (RolePrincipal) o;
    return Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

  @Override
  public String toString() {
    return "RolePrincipal{" + "name='" + name + '\'' + '}';
  }
}
