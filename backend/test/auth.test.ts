import { describe, expect, it } from "vitest";
import { hashPassword, signToken, verifyPassword, verifyToken } from "../src/auth";

describe("password hashing", () => {
  it("stores PBKDF2 hashes that verify against the original password", async () => {
    const hash = await hashPassword("correct horse battery staple");
    expect(hash.startsWith("pbkdf2$150000$")).toBe(true);
    expect(hash).not.toContain("correct horse");
    expect(await verifyPassword("correct horse battery staple", hash)).toBe(true);
    expect(await verifyPassword("wrong password", hash)).toBe(false);
  });

  it("rejects malformed stored hashes", async () => {
    expect(await verifyPassword("anything", "plain-text")).toBe(false);
    expect(await verifyPassword("anything", "pbkdf2$0$a$b")).toBe(false);
  });
});

describe("session tokens", () => {
  const secret = "test-secret-value-1234567890";

  it("round-trips a signed token", async () => {
    const token = await signToken("user-1", "user@example.com", secret, 60);
    const payload = await verifyToken(token, secret);
    expect(payload?.sub).toBe("user-1");
    expect(payload?.email).toBe("user@example.com");
  });

  it("rejects tokens signed with another secret and expired tokens", async () => {
    const token = await signToken("user-1", "user@example.com", secret, 60);
    expect(await verifyToken(token, "another-secret-value-123456")).toBeNull();
    const expired = await signToken("user-1", "user@example.com", secret, -10);
    expect(await verifyToken(expired, secret)).toBeNull();
  });
});
