package com.daniel.masterstudio

object Profiles {
    fun forName(name:String)=when {
        name.contains("Corrido") -> MasterSettings(.76f,-10.0f,-1.0f,28f,-.2f,-1.0f,1.15f,.9f,2.0f,-19f,.42f,1.02f)
        name.contains("Rap") -> MasterSettings(.74f,-9.8f,-1.0f,30f,0f,-1.4f,1.0f,.8f,2.0f,-18f,.48f,1.04f)
        name.contains("Reguetón") -> MasterSettings(.80f,-9.0f,-1.0f,28f,.3f,-1.0f,.7f,1.1f,2.1f,-18f,.38f,1.06f)
        name.contains("Cumbia") -> MasterSettings(.68f,-10.2f,-1.0f,30f,.2f,-.8f,.65f,.7f,1.8f,-18f,.32f,1.02f)
        name.contains("Pop") -> MasterSettings(.70f,-10.0f,-1.0f,28f,0f,-1.0f,1.0f,1.0f,1.8f,-19f,.38f,1.03f)
        else -> MasterSettings()
    }
}
