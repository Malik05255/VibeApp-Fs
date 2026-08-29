package com.almi.ai.ui.body

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.almi.ai.data.preferences.*
import java.util.Locale
import kotlin.math.*
import kotlinx.coroutines.delay

private val BG=Color(0xFF06111F); private val SUR=Color(0xFF0C1A2B); private val RAISED=Color(0xFF10233A)
private val TXT=Color(0xFFF7FAFF); private val MUT=Color(0xFF91A8C7); private val BLUE=Color(0xFF83BBFF)
private val RED=Color(0xFFFF443D); private val GREEN=Color(0xFF55D6A4); private const val CM=2.54f; private const val KG=.45359237f

@Composable
fun RealHuman3DBodyScreen(
    language:String, profile:BodyProfile, onHeightChanged:(Float)->Unit, onWeightChanged:(Float)->Unit,
    onMeasurementChanged:(BodyMeasurePoint,Float)->Unit, onMeasurementCleared:(BodyMeasurePoint)->Unit,
    onSnapshotReady:(String)->Unit={}, onComplete:()->Unit, modifier:Modifier=Modifier,
){
    var selectedName by rememberSaveable{ mutableStateOf<String?>(null) }
    val selected=selectedName?.let{ runCatching{ Target.valueOf(it) }.getOrNull() }
    var targetYaw by rememberSaveable{ mutableStateOf(0f) }
    var guide by remember(selectedName){ mutableStateOf(false) }
    LaunchedEffect(selectedName){ guide=false; if(selectedName!=null){ delay(160); guide=true } }
    val solved=remember(profile){ BodyShapeSolver.solve(profile) }
    val w by animateFloatAsState(solved.widthScale,tween(420),label="w"); val h by animateFloatAsState(solved.heightScale,tween(420),label="h")
    val d by animateFloatAsState(solved.depthScale,tween(420),label="d"); val yaw by animateFloatAsState(targetYaw,tween(300),label="yaw")
    val zoom by animateFloatAsState(selected?.zoom?:1f,tween(440),label="zoom"); val g by animateFloatAsState(if(guide)1f else 0f,tween(650),label="guide")
    fun open(t:Target){ selectedName=t.name; targetYaw=nearestYaw(yaw,t.yaw) }; fun close(){ selectedName=null; targetYaw=nearestYaw(yaw,0f) }
    val done=Target.entries.count{it.value(profile)!=null}+(if(profile.hasExplicitWeight)1 else 0); val total=Target.entries.size+1
    Column(modifier.fillMaxSize().background(BG)){
        Row(Modifier.fillMaxWidth().padding(18.dp,12.dp),Arrangement.SpaceBetween,Alignment.CenterVertically){
            Column{ Text("ALMI / BODY MAP",color=BLUE,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold); Text(tr(language,"قياسات جسمك","Your measurements"),color=TXT,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold) }
            Row(verticalAlignment=Alignment.CenterVertically){ Text("$done/$total",color=MUT); TextButton(onClick=onComplete){ Text(tr(language,"تم","Done"),color=TXT,fontWeight=FontWeight.Bold) } }
        }
        LinearProgressIndicator(progress={done.toFloat()/total},Modifier.fillMaxWidth().height(2.dp),color=BLUE,trackColor=Color.White.copy(.07f))
        Box(Modifier.weight(1f).fillMaxWidth()){
            BodyViewport(profile,selected,solved.copy(widthScale=w,heightScale=h,depthScale=d),yaw,zoom,g,{ if(selected==null)targetYaw+=it },::open,Modifier.fillMaxSize())
            if(selected==null) Surface(Modifier.align(Alignment.TopCenter).padding(top=12.dp),RoundedCornerShape(99.dp),SUR.copy(.9f),border=BorderStroke(1.dp,Color.White.copy(.08f))){ Text(tr(language,"اسحب 360°  •  اضغط النقطة الحمراء","Drag 360°  •  tap a red point"),Modifier.padding(14.dp,8.dp),color=MUT) }
            else MeasureCard(language,selected,selected.value(profile),{v-> if(selected==Target.HEIGHT)onHeightChanged(v/CM) else selected.point?.let{onMeasurementChanged(it,v/CM)}; close() },selected.point?.takeIf{it in profile.measurementsInches}?.let{p->{onMeasurementCleared(p)}},::close,Modifier.align(Alignment.TopCenter))
        }
        WeightDock(language,profile,onWeightChanged)
    }
}

@Composable private fun BodyViewport(profile:BodyProfile,selected:Target?,shape:DigitalTwinShape,yaw:Float,zoom:Float,g:Float,onYaw:(Float)->Unit,onSelect:(Target)->Unit,modifier:Modifier){
    var px by remember{mutableStateOf(IntSize.Zero)}; val inf=rememberInfiniteTransition(label="pulse"); val pulse by inf.animateFloat(0f,1f,infiniteRepeatable(tween(900),RepeatMode.Reverse),label="p")
    val label=remember{Paint(Paint.ANTI_ALIAS_FLAG).apply{color=TXT.toArgb();textSize=26f;typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD)}}
    val gestures=Modifier.onSizeChanged{px=it}.pointerInput(selected,yaw,shape,px){detectTapGestures{tap->if(selected==null&&px.width>0){val s=Size(px.width.toFloat(),px.height.toFloat());Target.entries.map{it to project(it.marker,s,yaw,shape,1f,null)}.minByOrNull{(it.second-tap).getDistance()}?.let{if((it.second-tap).getDistance()<55f)onSelect(it.first)}}}}.pointerInput(selected){detectDragGestures{c,a->c.consume();if(selected==null)onYaw(a.x*.78f)}}
    Box(modifier.then(gestures)){
        Canvas(Modifier.fillMaxSize()){
            grid(); human(shape,yaw,selected,zoom)
            Target.entries.forEach{t->val p=project(t.marker,size,yaw,shape,zoom,selected?.marker);val v=t.value(profile);val a=if(t==selected)20f+pulse*8 else 12f+pulse*4;drawCircle(RED.copy(.10f),a,p);drawCircle(RED.copy(.28f),a*.62f,p);drawCircle(Color(0xFFFF716B),if(t==selected)6.5f else 4.8f,p);if(v!=null&&selected==null)drawContext.canvas.nativeCanvas.drawText("${fmt(v)} cm",p.x+15,p.y-8,label)}
            selected?.let{t->val e=V(t.start.x+(t.end.x-t.start.x)*g,t.start.y+(t.end.y-t.start.y)*g,t.start.z+(t.end.z-t.start.z)*g);val a=project(t.start,size,yaw,shape,zoom,t.marker);val b=project(e,size,yaw,shape,zoom,t.marker);drawLine(BLUE.copy(.25f),a,b,9f,StrokeCap.Round);drawLine(BLUE,a,b,3.2f,StrokeCap.Round);arrow(a,b);if(g>.2f)arrow(b,a)}
        }
        if(selected==null)Surface(Modifier.align(Alignment.BottomCenter).padding(bottom=10.dp),RoundedCornerShape(99.dp),SUR.copy(.84f),border=BorderStroke(1.dp,Color.White.copy(.07f))){Text("360°  •  DRAG",Modifier.padding(13.dp,7.dp),color=MUT,style=MaterialTheme.typography.labelSmall)}
    }
}

private fun DrawScope.grid(){val step=size.width/7f;var x=0f;while(x<size.width){drawLine(Color.White.copy(.02f),Offset(x,0f),Offset(x,size.height));x+=step};var y=0f;while(y<size.height){drawLine(Color.White.copy(.018f),Offset(0f,y),Offset(size.width,y));y+=step};drawCircle(Color(0xFF2F6EAE).copy(.055f),size.width*.57f,Offset(size.width/2,size.height*.49f))}

private fun DrawScope.human(shape:DigitalTwinShape,yaw:Float,sel:Target?,z:Float){
    fun p(x:Float,y:Float,d:Float=0f)=project(V(x,y,d),size,yaw,shape,z,sel?.marker); val rad=Math.toRadians(yaw.toDouble());val c=cos(rad).toFloat();val s=sin(rad).toFloat();val front=abs(c);val back=c<0;val nearRight=s<=0
    val fill=Brush.horizontalGradient(listOf(Color(0xFF10284C).copy(.72f),Color(0xFF547CB6).copy(.78f),Color(0xFFD8E9FF).copy(.92f),Color(0xFF547CB6).copy(.76f),Color(0xFF10284C).copy(.70f)),size.width*.30f,size.width*.70f);val out=Color(0xFFE2EEFF).copy(.68f);val inner=Color(0xFFB9D7FF).copy(.23f)
    if(nearRight){arm(false,.58f,shape,yaw,sel,z,fill,out);leg(false,.66f,shape,yaw,sel,z,fill,out)}else{arm(true,.58f,shape,yaw,sel,z,fill,out);leg(true,.66f,shape,yaw,sel,z,fill,out)}
    val a=p(-.31f,.215f,.12f);val b=p(-.285f,.30f,.13f);val wl=p(-.195f,.455f,.105f);val hl=p(-.255f,.555f,.14f);val cl=p(-.055f,.602f,.10f);val cr=p(.055f,.602f,-.10f);val hr=p(.255f,.555f,-.14f);val wr=p(.195f,.455f,-.105f);val br=p(.285f,.30f,-.13f);val ar=p(.31f,.215f,-.12f)
    val torso=Path().apply{moveTo(a.x,a.y);cubicTo(p(-.33f,.26f,.13f).x,p(-.33f,.26f,.13f).y,b.x,b.y,wl.x,wl.y);cubicTo(wl.x,wl.y,hl.x,hl.y,cl.x,cl.y);lineTo(cr.x,cr.y);cubicTo(hr.x,hr.y,wr.x,wr.y,wr.x,wr.y);cubicTo(br.x,br.y,p(.33f,.26f,-.13f).x,p(.33f,.26f,-.13f).y,ar.x,ar.y);close()};drawPath(torso,fill);drawPath(torso,out.copy(.16f),style=Stroke(8f*z));drawPath(torso,out,style=Stroke(1.4f*z))
    val neck=Path().apply{val n1=p(-.075f,.135f,.065f);val n2=p(-.105f,.195f,.08f);val n3=p(.105f,.195f,-.08f);val n4=p(.075f,.135f,-.065f);moveTo(n1.x,n1.y);lineTo(n2.x,n2.y);lineTo(n3.x,n3.y);lineTo(n4.x,n4.y);close()};drawPath(neck,fill);drawPath(neck,out,style=Stroke(1.3f*z))
    val head=Path().apply{val t=p(0f,.028f);val l=p(-.09f,.065f,.06f);val lj=p(-.065f,.125f,.05f);val ch=p(0f,.145f);val rj=p(.065f,.125f,-.05f);val r=p(.09f,.065f,-.06f);moveTo(t.x,t.y);cubicTo(p(-.06f,.02f,.04f).x,p(-.06f,.02f,.04f).y,l.x,l.y,lj.x,lj.y);cubicTo(lj.x,lj.y,ch.x,ch.y,ch.x,ch.y);cubicTo(rj.x,rj.y,r.x,r.y,r.x,r.y);cubicTo(p(.06f,.02f,-.04f).x,p(.06f,.02f,-.04f).y,t.x,t.y,t.x,t.y);close()};drawPath(head,fill);drawPath(head,out.copy(.17f),style=Stroke(8f*z));drawPath(head,out,style=Stroke(1.4f*z))
    if(front>.28f){if(!back){drawLine(inner,p(0f,.205f),p(0f,.455f),1.2f*z);drawLine(inner,p(-.24f,.29f,.10f),p(-.02f,.325f,.02f),1.2f*z);drawLine(inner,p(.24f,.29f,-.10f),p(.02f,.325f,-.02f),1.2f*z);repeat(3){i->val yy=.355f+i*.044f;drawLine(inner.copy(.7f),p(-.115f,yy,.06f),p(.115f,yy,-.06f),1f*z)};drawLine(inner,p(-.045f,.083f,.04f),p(.045f,.083f,-.04f),1f*z);drawLine(inner.copy(.7f),p(0f,.075f),p(0f,.118f),1f*z)}else{drawLine(inner,p(0f,.17f),p(0f,.585f),1.3f*z);drawLine(inner,p(-.25f,.26f,.10f),p(-.08f,.36f,.04f),1.2f*z);drawLine(inner,p(.25f,.26f,-.10f),p(.08f,.36f,-.04f),1.2f*z)}}
    if(nearRight){arm(true,.98f,shape,yaw,sel,z,fill,out);leg(true,.98f,shape,yaw,sel,z,fill,out)}else{arm(false,.98f,shape,yaw,sel,z,fill,out);leg(false,.98f,shape,yaw,sel,z,fill,out)}
    val gr=p(0f,.988f);drawOval(Color.Black.copy(.28f),Offset(gr.x-70*z,gr.y-5),Size(140*z,13*z))
}

private fun DrawScope.arm(right:Boolean,alpha:Float,shape:DigitalTwinShape,yaw:Float,sel:Target?,z:Float,fill:Brush,out:Color){val s=if(right)1f else -1f;val dz=if(right)-1f else 1f;fun p(x:Float,y:Float,d:Float=0f)=project(V(x,y,d),size,yaw,shape,z,sel?.marker);val so=p(s*.318f,.222f,dz*.105f);val si=p(s*.265f,.246f,dz*.06f);val eo=p(s*.465f,.405f,dz*.07f);val ei=p(s*.414f,.405f,dz*.04f);val wo=p(s*.555f,.575f,dz*.055f);val wi=p(s*.520f,.575f,dz*.03f);val ho=p(s*.592f,.645f,dz*.045f);val hi=p(s*.552f,.650f,dz*.024f);val q=Path().apply{moveTo(so.x,so.y);cubicTo(p(s*.39f,.30f,dz*.09f).x,p(s*.39f,.30f,dz*.09f).y,eo.x,eo.y,wo.x,wo.y);lineTo(ho.x,ho.y);lineTo(hi.x,hi.y);cubicTo(wi.x,wi.y,ei.x,ei.y,si.x,si.y);close()};drawPath(q,fill,alpha);drawPath(q,out.copy(alpha*.9f),style=Stroke(1.2f*z));val palm=p(s*.565f,.61f,dz*.035f);repeat(4){i->drawLine(Color(0xFFDBEAFF).copy(.20f*alpha),palm,p(s*(.57f+i*.008f),.645f+i*.002f,dz*.025f),1f*z)}}
private fun DrawScope.leg(right:Boolean,alpha:Float,shape:DigitalTwinShape,yaw:Float,sel:Target?,z:Float,fill:Brush,out:Color){val s=if(right)1f else -1f;val dz=if(right)-1f else 1f;fun p(x:Float,y:Float,d:Float=0f)=project(V(x,y,d),size,yaw,shape,z,sel?.marker);val ho=p(s*.245f,.555f,dz*.135f);val hi=p(s*.055f,.603f,dz*.09f);val ko=p(s*.165f,.765f,dz*.08f);val ki=p(s*.07f,.765f,dz*.045f);val ao=p(s*.13f,.935f,dz*.055f);val ai=p(s*.07f,.935f,dz*.03f);val to=p(s*.155f,.982f,dz*.025f);val ti=p(s*.035f,.982f,dz*.018f);val q=Path().apply{moveTo(ho.x,ho.y);cubicTo(p(s*.22f,.65f,dz*.115f).x,p(s*.22f,.65f,dz*.115f).y,ko.x,ko.y,ao.x,ao.y);lineTo(to.x,to.y);lineTo(ti.x,ti.y);cubicTo(ai.x,ai.y,ki.x,ki.y,hi.x,hi.y);close()};drawPath(q,fill,alpha);drawPath(q,out.copy(alpha*.9f),style=Stroke(1.2f*z));drawCircle(Color(0xFFCAE0FF).copy(.16f*alpha),9*z,p(s*.12f,.765f,dz*.06f));drawLine(Color(0xFFCAE0FF).copy(.15f*alpha),p(s*.12f,.765f,dz*.06f),p(s*.10f,.93f,dz*.04f),1.5f*z)}

private fun DrawScope.arrow(a:Offset,b:Offset){val dx=b.x-a.x;val dy=b.y-a.y;val len=sqrt(dx*dx+dy*dy).coerceAtLeast(1f);val ux=dx/len;val uy=dy/len;val px=-uy;val py=ux;val x=Offset(b.x-ux*16+px*7,b.y-uy*16+py*7);val y=Offset(b.x-ux*16-px*7,b.y-uy*16-py*7);drawLine(BLUE,b,x,3.2f,StrokeCap.Round);drawLine(BLUE,b,y,3.2f,StrokeCap.Round)}
private fun project(v:V,size:Size,yaw:Float,shape:DigitalTwinShape,zoom:Float,focus:V?):Offset{val r=Math.toRadians(yaw.toDouble());val c=cos(r).toFloat();val s=sin(r).toFloat();val rx=v.x*c*shape.widthScale-v.z*s*shape.depthScale*.95f;val raw=Offset(size.width*.5f+rx*size.width*.50f,size.height*.045f+v.y*(size.height*.87f*shape.heightScale.coerceIn(.80f,1.18f)));if(focus==null||abs(zoom-1f)<.001f)return raw;val f=project(focus,size,yaw,shape,1f,null);val t=Offset(size.width*.5f,size.height*.57f);return Offset(t.x+(raw.x-f.x)*zoom,t.y+(raw.y-f.y)*zoom)}

@Composable private fun MeasureCard(language:String,target:Target,existing:Float?,save:(Float)->Unit,clear:(()->Unit)?,close:()->Unit,modifier:Modifier){var raw by remember(target,existing){mutableStateOf(existing?.let(::fmt).orEmpty())};var tried by remember(target){mutableStateOf(false)};val n=raw.replace(',','.').toFloatOrNull();val ok=n?.let(target::valid)==true;Surface(modifier.fillMaxWidth().padding(17.dp,10.dp),RoundedCornerShape(20.dp),RAISED,border=BorderStroke(1.dp,if(tried&&!ok)RED.copy(.7f) else Color.White.copy(.10f)),shadowElevation=12.dp){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){Row(Modifier.fillMaxWidth(),Arrangement.SpaceBetween,Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(target.title(language),color=TXT,fontWeight=FontWeight.Bold);Text(target.note(language),color=MUT,style=MaterialTheme.typography.bodySmall)};IconButton(onClick=close){Icon(Icons.Rounded.Close,null,tint=MUT)}};Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(raw,{raw=it.filter{c->c.isDigit()||c=='.'||c==','}.take(6);tried=false},Modifier.weight(1f),singleLine=true,suffix={Text("cm")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),colors=OutlinedTextFieldDefaults.colors(focusedTextColor=TXT,unfocusedTextColor=TXT,focusedBorderColor=if(ok)GREEN else BLUE,unfocusedBorderColor=Color.White.copy(.15f),cursorColor=BLUE));Surface(Modifier.size(44.dp),CircleShape,if(ok)GREEN.copy(.14f) else RED.copy(.10f),border=BorderStroke(1.dp,if(ok)GREEN.copy(.55f) else RED.copy(.25f))){Box(contentAlignment=Alignment.Center){Icon(if(ok)Icons.Rounded.Check else Icons.Rounded.Close,null,tint=if(ok)GREEN else RED.copy(.6f))}}};if(tried&&!ok)Text(tr(language,"أدخل قياسًا صحيحًا بالسنتيمتر.","Enter a valid measurement in centimeters."),color=RED,style=MaterialTheme.typography.labelSmall);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){if(clear!=null)TextButton(onClick={clear();raw=""}){Text(tr(language,"مسح","Clear"),color=MUT)};Button(onClick={tried=true;if(ok&&n!=null)save(n)},colors=ButtonDefaults.buttonColors(containerColor=BLUE)){Icon(Icons.Rounded.Check,null,tint=BG);Spacer(Modifier.width(5.dp));Text(tr(language,"حفظ","Save"),color=BG,fontWeight=FontWeight.Bold)}}}}}

@Composable private fun WeightDock(language:String,profile:BodyProfile,save:(Float)->Unit){var raw by remember(profile.hasExplicitWeight,profile.weightKilograms){mutableStateOf(profile.weightKilograms.takeIf{profile.hasExplicitWeight}?.let(::fmt).orEmpty())};val n=raw.replace(',','.').toFloatOrNull();val ok=n!=null&&n in 25f..350f;Surface(Modifier.fillMaxWidth().navigationBarsPadding().padding(14.dp,9.dp),RoundedCornerShape(22.dp),SUR,border=BorderStroke(1.dp,Color.White.copy(.10f))){Row(Modifier.padding(15.dp,11.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Column(Modifier.weight(1f)){Text(tr(language,"الوزن","Weight"),color=TXT,fontWeight=FontWeight.Bold);Text(tr(language,"يتفاعل حجم الجسم مباشرة","Body volume reacts immediately"),color=MUT,style=MaterialTheme.typography.labelSmall)};OutlinedTextField(raw,{raw=it.filter{c->c.isDigit()||c=='.'||c==','}.take(6)},Modifier.width(126.dp),singleLine=true,suffix={Text("kg")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),colors=OutlinedTextFieldDefaults.colors(focusedTextColor=TXT,unfocusedTextColor=TXT,focusedBorderColor=if(ok)GREEN else BLUE,unfocusedBorderColor=Color.White.copy(.15f),cursorColor=BLUE));IconButton(onClick={if(ok&&n!=null)save(n/KG)},enabled=ok){Icon(Icons.Rounded.Check,null,tint=if(ok)GREEN else MUT)}}}}

private data class V(val x:Float,val y:Float,val z:Float=0f)
private enum class Target(val point:BodyMeasurePoint?,val marker:V,val start:V,val end:V,val yaw:Float,val zoom:Float,val lo:Float,val hi:Float,val ar:String,val en:String,val arN:String,val enN:String){
HEIGHT(null,V(-.44f,.50f,.02f),V(-.44f,.025f,.02f),V(-.44f,.982f,.02f),0f,1.30f,90f,240f,"الطول","Height","من أعلى الرأس إلى أسفل القدم.","From the top of the head to the floor."),NECK(BodyMeasurePoint.NECK,V(.12f,.166f,-.04f),V(-.095f,.166f,.05f),V(.095f,.166f,-.05f),0f,1.82f,20f,70f,"محيط الرقبة","Neck","لف الشريط حول قاعدة الرقبة.","Measure around the base of the neck."),SHOULDERS(BodyMeasurePoint.SHOULDERS,V(.315f,.218f,-.10f),V(-.305f,.218f,.10f),V(.305f,.218f,-.10f),0f,1.62f,25f,80f,"عرض الكتفين","Shoulders","من نهاية كتف إلى نهاية الكتف الآخر.","From one shoulder edge to the other."),CHEST(BodyMeasurePoint.CHEST,V(.295f,.315f,-.11f),V(-.29f,.315f,.11f),V(.29f,.315f,-.11f),0f,1.60f,45f,180f,"محيط الصدر","Chest","حول أعرض نقطة في الصدر.","Around the fullest part of the chest."),WAIST(BodyMeasurePoint.WAIST,V(.225f,.455f,-.10f),V(-.22f,.455f,.10f),V(.22f,.455f,-.10f),0f,1.65f,40f,180f,"محيط الخصر","Waist","حول الخصر الطبيعي بدون شد الشريط.","Around the natural waist without pulling tight."),HIPS(BodyMeasurePoint.HIPS,V(.275f,.555f,-.13f),V(-.27f,.555f,.13f),V(.27f,.555f,-.13f),0f,1.60f,50f,190f,"محيط الورك","Hips","حول أعرض نقطة في الورك.","Around the fullest part of the hips."),ARM_LENGTH(BodyMeasurePoint.ARM_LENGTH,V(.505f,.435f,-.05f),V(.30f,.225f,-.09f),V(.548f,.575f,-.04f),-10f,1.80f,35f,90f,"طول الذراع","Arm length","من بداية الكتف إلى نهاية الرسغ.","From the shoulder point to the wrist."),WRIST(BodyMeasurePoint.WRIST,V(.545f,.575f,-.04f),V(.505f,.575f,.02f),V(.565f,.575f,-.05f),-15f,2.05f,10f,35f,"محيط الرسغ","Wrist","لف الشريط حول مفصل الرسغ.","Measure around the wrist joint."),HAND(BodyMeasurePoint.HAND,V(.578f,.62f,-.03f),V(.548f,.58f,-.03f),V(.592f,.646f,-.03f),-18f,2.28f,12f,30f,"طول اليد","Hand length","من بداية الكف عند الرسغ إلى نهاية أطول إصبع.","From the wrist crease to the tip of the longest finger."),THIGH(BodyMeasurePoint.THIGH,V(.205f,.665f,-.10f),V(.06f,.665f,.05f),V(.245f,.665f,-.11f),8f,1.82f,25f,100f,"محيط الفخذ","Thigh","حول أعرض نقطة في أعلى الفخذ.","Around the fullest part of the upper thigh."),INSEAM(BodyMeasurePoint.INSEAM,V(.045f,.775f,-.02f),V(.045f,.605f,-.02f),V(.045f,.95f,-.02f),0f,1.58f,45f,110f,"طول الساق الداخلي","Inseam","من أعلى الفخذ الداخلي إلى الأرض.","From the inner crotch seam down to the floor."),CALF(BodyMeasurePoint.CALF,V(.155f,.835f,-.06f),V(.065f,.835f,.03f),V(.19f,.835f,-.07f),8f,1.90f,20f,70f,"محيط الساق","Calf","حول أعرض نقطة في بطة الساق.","Around the widest part of the calf."),FOOT(BodyMeasurePoint.FOOT,V(.145f,.97f,-.02f),V(.035f,.97f,.01f),V(.17f,.97f,-.02f),8f,2.05f,15f,40f,"طول القدم","Foot length","من مؤخرة الكعب إلى نهاية أطول إصبع.","From the back of the heel to the longest toe.");fun title(l:String)=if(l=="ar")ar else en;fun note(l:String)=if(l=="ar")arN else enN;fun valid(v:Float)=v.isFinite()&&v in lo..hi;fun value(p:BodyProfile):Float?=if(this==HEIGHT)p.heightCentimeters.takeIf{p.hasExplicitHeight}else point?.let{p.measurementsInches[it]}?.times(CM)}
private fun nearestYaw(cur:Float,pref:Float):Float{val n=((cur%360)+360)%360;var d=pref-n;while(d>180)d-=360;while(d< -180)d+=360;return cur+d}
private fun fmt(v:Float)=if(abs(v-v.roundToInt())<.05f)v.roundToInt().toString() else String.format(Locale.US,"%.1f",v)
private fun tr(l:String,ar:String,en:String)=if(l=="ar")ar else en
