package io.github.stream29.kode.oauth.core

public interface OAuthTokenStore {
    public suspend fun load(storage: String, key: String): OAuthTokenRecord?

    public suspend fun save(storage: String, key: String, token: OAuthTokenRecord)

    public suspend fun delete(storage: String, key: String)
}
