package com.daniel.masterstudio

object Profiles {
    fun forName(name: String): MasterSettings = when {
        name.contains("Corrido") -> MasterSettings(.74f, -10.0f, -1.0f, 28f, -.2f, -1.0f, 1.0f, .75f, 1.9f, -18f, .38f, 1.02f)
        name.contains("Rap") -> MasterSettings(.72f, -9.8f, -1.0f, 30f, 0f, -1.2f, .9f, .7f, 1.9f, -17.5f, .42f, 1.03f)
        name.contains("Reguetón") -> MasterSettings(.76f, -9.0f, -1.0f, 28f, .25f, -.9f, .65f, .85f, 2.0f, -17.5f, .34f, 1.04f)
        name.contains("Cumbia") -> MasterSettings(.66f, -10.2f, -1.0f, 30f, .15f, -.8f, .55f, .55f, 1.7f, -18f, .28f, 1.02f)
        name.contains("Pop") -> MasterSettings(.68f, -10.0f, -1.0f, 28f, 0f, -.9f, .85f, .8f, 1.8f, -18f, .34f, 1.03f)
        else -> MasterSettings()
    }
}
