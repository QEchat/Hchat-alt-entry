package android.content
open class Context
interface SharedPreferences {
 fun getString(key:String, default:String?):String?
 fun edit():Editor
 interface Editor { fun clear():Editor; fun putString(key:String,value:String?):Editor; fun remove(key:String):Editor; fun apply(); fun commit():Boolean }
}