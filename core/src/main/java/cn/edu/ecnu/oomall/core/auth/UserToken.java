package cn.edu.ecnu.oomall.core.auth;

import java.io.Serializable;

/** Redis Session 中保存的、不可由客户端伪造的最小身份信息。 */
public record UserToken(Long userId, Role role) implements Serializable {
}
