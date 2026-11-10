package com.runmoni.app.domain

import java.text.Normalizer
import java.util.Locale

/** Shared device/community naming policy; it does not merge authentication identities. */
object NicknamePolicy {
    const val MIN_LENGTH = 2
    const val MAX_LENGTH = 16
    private val spaces = Regex("[\\s\\p{Z}]+")
    private val characters = Regex("[A-Za-z0-9_가-힣-]+(?: [A-Za-z0-9_가-힣-]+)*")
    private val letterOrNumber = Regex(".*[A-Za-z0-9가-힣].*")

    fun normalize(raw: String): String = Normalizer.normalize(raw, Normalizer.Form.NFC)
        .trim().replace(spaces, " ")

    /** Only ASCII English letters are case folded; the accepted alphabet keeps rules equivalent. */
    fun key(raw: String): String = normalize(raw).lowercase(Locale.ROOT)

    fun validationError(raw: String): String? {
        val name = normalize(raw)
        return when {
            name.length !in MIN_LENGTH..MAX_LENGTH -> "닉네임은 2~16자로 입력해 주세요."
            !characters.matches(name) || !letterOrNumber.matches(name) ->
                "한글·영문·숫자와 공백, 밑줄, 하이픈을 사용할 수 있어요."
            else -> null
        }
    }

    fun requireValid(raw: String): NormalizedNickname {
        require(validationError(raw) == null) { validationError(raw).orEmpty() }
        return NormalizedNickname(normalize(raw), key(raw))
    }
}

data class NormalizedNickname(val nickname: String, val key: String)
enum class NicknameAvailability { AVAILABLE, OWNED, TAKEN, UNVERIFIED }

/** A point-in-time server observation, never authorization for a later save. */
data class NicknameCheck(
    val nickname: String,
    val key: String,
    val checkedForUid: String,
    val availability: NicknameAvailability,
) {
    fun matches(raw: String, uid: String): Boolean = nickname == NicknamePolicy.normalize(raw) &&
        key == NicknamePolicy.key(raw) && checkedForUid == uid
}

class NicknameTakenException : IllegalStateException("이미 사용 중인 닉네임이에요. 다른 닉네임을 선택해 주세요.")
