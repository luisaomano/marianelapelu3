package uy.negocio.offline

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

data class Appointment(val id:Long,val date:String,val time:String,val name:String,val service:String,val phone:String="",val duration:Int=60,val status:String="Pendiente")
data class Payment(val id:Long,val date:String,val amountCents:Long,val service:String,val appointmentId:Long?=null)
data class Service(val id:Long,val name:String,val priceCents:Long,val minutes:Int)

class Database(context:Context):SQLiteOpenHelper(context,"negocio.db",null,4) {
 override fun onCreate(db:SQLiteDatabase) {
  db.execSQL("CREATE TABLE appointments(id INTEGER PRIMARY KEY AUTOINCREMENT,date TEXT NOT NULL,time TEXT NOT NULL,name TEXT NOT NULL,service TEXT NOT NULL,phone TEXT NOT NULL DEFAULT '',duration INTEGER NOT NULL DEFAULT 60,status TEXT NOT NULL DEFAULT 'Pendiente')")
  db.execSQL("CREATE TABLE payments(id INTEGER PRIMARY KEY AUTOINCREMENT,date TEXT NOT NULL,amount_cents INTEGER NOT NULL CHECK(amount_cents>0),service TEXT NOT NULL,appointment_id INTEGER)")
  db.execSQL("CREATE TABLE client_profiles(name TEXT PRIMARY KEY COLLATE NOCASE, phone TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '')")
  db.execSQL("CREATE TABLE services(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE,price_cents INTEGER NOT NULL DEFAULT 0,minutes INTEGER NOT NULL DEFAULT 60)")
  createIndexes(db)
 }
 private fun createIndexes(db:SQLiteDatabase) {
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_appointments_date ON appointments(date,time)")
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_payments_date ON payments(date)")
 }
 override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) {
  if(oldVersion<2) {
   db.execSQL("ALTER TABLE appointments ADD COLUMN phone TEXT NOT NULL DEFAULT ''")
   db.execSQL("ALTER TABLE appointments ADD COLUMN duration INTEGER NOT NULL DEFAULT 60")
   db.execSQL("ALTER TABLE appointments ADD COLUMN status TEXT NOT NULL DEFAULT 'Pendiente'")
   db.execSQL("ALTER TABLE payments ADD COLUMN appointment_id INTEGER")
   db.execSQL("CREATE TABLE IF NOT EXISTS services(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE,price_cents INTEGER NOT NULL DEFAULT 0,minutes INTEGER NOT NULL DEFAULT 60)")
   createIndexes(db)
  }
  if(oldVersion<3) db.execSQL("CREATE TABLE IF NOT EXISTS client_profiles(name TEXT PRIMARY KEY COLLATE NOCASE, phone TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '')")
  if(oldVersion<4) db.execSQL("DROP TABLE IF EXISTS expenses")
 }
 private fun cv(vararg p:Pair<String,Any?>)=ContentValues().apply { p.forEach { (k,v)->when(v) { null->putNull(k); is String->put(k,v);is Int->put(k,v);is Long->put(k,v) } } }
 fun saveAppointment(a:Appointment):Long {
  val v=cv("date" to a.date,"time" to a.time,"name" to a.name,"service" to a.service,"phone" to a.phone,"duration" to a.duration,"status" to a.status)
  return if(a.id==0L) writableDatabase.insertOrThrow("appointments",null,v) else { writableDatabase.update("appointments",v,"id=?",arrayOf(a.id.toString()));a.id }
 }
 fun savePayment(p:Payment):Long {
  val v=cv("date" to p.date,"amount_cents" to p.amountCents,"service" to p.service,"appointment_id" to p.appointmentId)
  return if(p.id==0L) writableDatabase.insertOrThrow("payments",null,v) else { writableDatabase.update("payments",v,"id=?",arrayOf(p.id.toString()));p.id }
 }
 fun saveService(s:Service):Long {
  val v=cv("name" to s.name,"price_cents" to s.priceCents,"minutes" to s.minutes)
  return if(s.id==0L) writableDatabase.insertOrThrow("services",null,v) else { writableDatabase.update("services",v,"id=?",arrayOf(s.id.toString()));s.id }
 }
 fun appointments():List<Appointment> { val out=mutableListOf<Appointment>();readableDatabase.rawQuery("SELECT id,date,time,name,service,phone,duration,status FROM appointments ORDER BY date,time,id",null).use { c->while(c.moveToNext()) out.add(Appointment(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getInt(6),c.getString(7))) };return out }
 fun payments():List<Payment> { val out=mutableListOf<Payment>();readableDatabase.rawQuery("SELECT id,date,amount_cents,service,appointment_id FROM payments ORDER BY date DESC,id DESC",null).use { c->while(c.moveToNext()) out.add(Payment(c.getLong(0),c.getString(1),c.getLong(2),c.getString(3),if(c.isNull(4)) null else c.getLong(4))) };return out }
 fun services():List<Service> { val out=mutableListOf<Service>();readableDatabase.rawQuery("SELECT id,name,price_cents,minutes FROM services ORDER BY name",null).use { c->while(c.moveToNext()) out.add(Service(c.getLong(0),c.getString(1),c.getLong(2),c.getInt(3))) };return out }
 fun delete(table:String,id:Long) { require(table in setOf("appointments","payments","services"));writableDatabase.delete(table,"id=?",arrayOf(id.toString())) }
 fun conflicts(a:Appointment):Boolean {
  val start=runCatching { java.time.LocalTime.parse(a.time).toSecondOfDay()/60 }.getOrDefault(0)
  return appointments().any { b->b.id!=a.id && b.date==a.date && b.status!="Cancelado" && runCatching { val x=java.time.LocalTime.parse(b.time).toSecondOfDay()/60;start<x+b.duration && x<start+a.duration }.getOrDefault(false) }
 }
 fun profile(name:String):Pair<String,String> { readableDatabase.rawQuery("SELECT phone,notes FROM client_profiles WHERE name=?",arrayOf(name)).use { c->if(c.moveToFirst())return c.getString(0) to c.getString(1) };return "" to "" }
 fun saveProfile(name:String,phone:String,notes:String) { writableDatabase.insertWithOnConflict("client_profiles",null,cv("name" to name,"phone" to phone,"notes" to notes),SQLiteDatabase.CONFLICT_REPLACE) }
 fun profileNames():List<String> { val out=mutableListOf<String>();readableDatabase.rawQuery("SELECT name FROM client_profiles ORDER BY name",null).use { c->while(c.moveToNext())out.add(c.getString(0)) };return out }
 fun backup():String {
  val root=JSONObject().put("format","agenda-ingresos-offline").put("version",2)
  val a=JSONArray();appointments().forEach { a.put(JSONObject().put("id",it.id).put("date",it.date).put("time",it.time).put("name",it.name).put("service",it.service).put("phone",it.phone).put("duration",it.duration).put("status",it.status)) };root.put("appointments",a)
  val p=JSONArray();payments().forEach { p.put(JSONObject().put("id",it.id).put("date",it.date).put("amount",it.amountCents).put("service",it.service).put("appointmentId",it.appointmentId)) };root.put("payments",p)
  val s=JSONArray();services().forEach { s.put(JSONObject().put("id",it.id).put("name",it.name).put("price",it.priceCents).put("minutes",it.minutes)) };root.put("services",s)
  val profiles=JSONArray();profileNames().forEach { val (phone,notes)=profile(it);profiles.put(JSONObject().put("name",it).put("phone",phone).put("notes",notes)) };root.put("clientProfiles",profiles)
  return root.toString(2)
 }
 fun restore(json:String) {
  val root=JSONObject(json);require(root.optString("format")=="agenda-ingresos-offline" && root.optInt("version")==2) { "Formato de respaldo no compatible" }
  val arrays=listOf("appointments","payments","services").associateWith { root.getJSONArray(it) }
  val db=writableDatabase;db.beginTransaction()
  try {
   listOf("payments","appointments","services","client_profiles").forEach { db.delete(it,null,null) }
   arrays.getValue("appointments").let { arr->for(i in 0 until arr.length()) { val o=arr.getJSONObject(i);db.insertOrThrow("appointments",null,cv("id" to o.getLong("id"),"date" to o.getString("date"),"time" to o.getString("time"),"name" to o.getString("name"),"service" to o.getString("service"),"phone" to o.optString("phone"),"duration" to o.getInt("duration"),"status" to o.getString("status"))) } }
   arrays.getValue("payments").let { arr->for(i in 0 until arr.length()) { val o=arr.getJSONObject(i);db.insertOrThrow("payments",null,cv("id" to o.getLong("id"),"date" to o.getString("date"),"amount_cents" to o.getLong("amount"),"service" to o.getString("service"),"appointment_id" to if(o.isNull("appointmentId")) null else o.getLong("appointmentId"))) } }
   arrays.getValue("services").let { arr->for(i in 0 until arr.length()) { val o=arr.getJSONObject(i);db.insertOrThrow("services",null,cv("id" to o.getLong("id"),"name" to o.getString("name"),"price_cents" to o.getLong("price"),"minutes" to o.getInt("minutes"))) } }
   root.optJSONArray("clientProfiles")?.let { arr->for(i in 0 until arr.length()) { val o=arr.getJSONObject(i);db.insertOrThrow("client_profiles",null,cv("name" to o.getString("name"),"phone" to o.optString("phone"),"notes" to o.optString("notes"))) } }
   db.setTransactionSuccessful()
  } finally { db.endTransaction() }
 }
}
