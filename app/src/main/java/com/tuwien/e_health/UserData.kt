package com.tuwien.e_health

data class UserData
    (
    val email: String? = null,
    val personName: String? = null,
    val age: Long? = -1,
    val sportMode: Boolean = false
    )
{
    // default values create a no-argument default constructor, which is needed
    // for deserialization from a DataSnapshot.
}
