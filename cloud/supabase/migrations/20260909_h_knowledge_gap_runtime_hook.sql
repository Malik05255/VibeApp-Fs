-- Automatically connect H's live chat runtime to the durable Learning Queue.
--
-- The trigger is deliberately conservative. It stores only the unresolved user question
-- when H itself emits an explicit uncertainty / verification-failure signal. Secrets,
-- action requests, greetings and time-volatile questions are excluded before queueing.
-- Candidate answers, prompts, chain-of-thought and raw media never enter this path.

create or replace function public.h_capture_chat_knowledge_gap()
returns trigger
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_query text;
  v_query_norm text;
  v_key text;
  v_reply text;
  v_reason text;
  v_priority text := 'medium';
begin
  if new.role <> 'assistant' or btrim(coalesce(new.body, '')) = '' then
    return new;
  end if;

  v_reply := lower(btrim(new.body));
  v_reply := translate(v_reply, 'أإآىؤئ', 'ااايوي');
  v_reply := translate(v_reply, 'ًٌٍَُِّْٰٕٖٜٟٓٔٗ٘ٙٚٛٝٞ', '');

  -- Recognize only explicit H uncertainty / verification failure signals.
  if v_reply ~ E'(التحقق\\s+النهاي[يي]\\s+ما\\s+اكتمل|ما\\s+حصلت\\s+ادل[هة]\\s+كافي[هة]|مسار\\s+الذكاء\\s+السحابي\\s+المجاني\\s+لم\\s+يعط|لا\\s+اعرف|ما\\s+اعرف|لا\\s+ادري|غير\\s+متاكد|لست\\s+متاكد|لم\\s+اتمكن\\s+من\\s+التحقق|لا\\s+يمكنني\\s+التحقق|لا\\s+توجد\\s+ادل[هة]\\s+موثوق[هة]|لا\\s+يوجد\\s+دليل\\s+موثوق|i\\s+don''?t\\s+know|not\\s+sure|cannot\\s+verify|can''?t\\s+verify|no\\s+verified\\s+evidence|insufficient\\s+evidence)' then
    if v_reply ~ E'(التحقق\\s+النهاي[يي]\\s+ما\\s+اكتمل|نتيج[هة]\\s+غير\\s+موكد[هة])' then
      v_reason := 'verifier_rejected';
    elsif v_reply ~ E'(ما\\s+حصلت\\s+ادل[هة]\\s+كافي[هة]|no\\s+verified\\s+evidence|insufficient\\s+evidence)' then
      v_reason := 'research_no_evidence';
    elsif v_reply ~ E'(مسار\\s+الذكاء\\s+السحابي\\s+المجاني\\s+لم\\s+يعط)' then
      v_reason := 'tool_unavailable';
    else
      v_reason := 'explicit_uncertainty';
    end if;
  else
    return new;
  end if;

  -- Use the latest user turn from the same owner/conversation. H chat writes user turns
  -- before assistant turns, so this captures the question without copying the reply.
  select c.body
    into v_query
    from public.h_runtime_chat c
   where c.user_key = new.user_key
     and c.role = 'user'
     and c.conversation_id is not distinct from new.conversation_id
     and c.created_at <= new.created_at
   order by c.created_at desc
   limit 1;

  v_query := left(regexp_replace(btrim(coalesce(v_query, '')), E'\\s+', ' ', 'g'), 600);
  if v_query = '' then
    return new;
  end if;

  v_query_norm := lower(v_query);
  v_query_norm := translate(v_query_norm, 'أإآىؤئ', 'ااايوي');
  v_query_norm := translate(v_query_norm, 'ًٌٍَُِّْٰٕٖٜٟٓٔٗ٘ٙٚٛٝٞ', '');

  -- Fail closed for secrets / credentials / payment-card-like material.
  if v_query_norm ~ E'(password|passcode|secret|api\\s*key|access\\s*token|refresh\\s*token|bearer|private\\s*key|cvv|cvc|otp|pin([^a-z]|$)|كلم[هة]\\s*المرور|رمز\\s*التحقق|رمز\\s*الدخول|الرقم\\s*السري|مفتاح\\s*(api|اي\\s*بي\\s*اي)|توكن)'
     or v_query_norm ~ E'([0-9][ -]*){13,19}'
     or v_query_norm ~ E'(otp|رمز\\s*التحقق|رمز\\s*الدخول)[^0-9]{0,12}[0-9]{4,8}' then
    return new;
  end if;

  -- Never turn moment-specific answers into durable learned facts.
  if v_query_norm ~ E'(today|now|current|currently|latest|live|breaking|اليوم|الان|حاليا|الحالي|الحاليه|احدث|اخر\\s*(خبر|تصريح|نتيج[هة]|سعر)|عاجل|طقس|درج[هة]\\s*الحرار[هة]|weather|forecast|سعر\\s*(السهم|سهم|العمله|عمل[هة]|بيتكوين|بتكوين|كريبتو)|stock\\s*price|crypto\\s*price|exchange\\s*rate|نتيج[هة]\\s*(المباراه|مبارا[هة])|نتايج\\s*(المباريات|مباريات)|ترتيب\\s*(الدوري|الفرق)|score|standings|مفتوح\\s*الان|متوفر\\s*الان|available\\s*now|availability|سعر\\s*(الفندق|فندق)|اسعار\\s*الفنادق|hotel\\s*(rate|price))' then
    return new;
  end if;

  -- Greetings and executable/action requests are not knowledge gaps.
  if v_query_norm ~ E'^(هلا|هلا والله|مرحبا|السلام عليكم|صباح الخير|مساء الخير|شكرا|thanks?|hello|hi)(\\s|$)'
     or v_query_norm ~ E'(ذكرني|تذكير|ارسل\\s*رسال[هة]|ارسلي\\s*رسال[هة]|اتصل\\s*ب|افتح\\s*|شغل\\s*|اكتب\\s*لي|صمم\\s*لي|عدل\\s*|احذف\\s*|remind\\s*me|send\\s*(a\\s*)?message|call\\s*|write\\s*me|design\\s*)' then
    return new;
  end if;

  -- Require a factual-question shape before durable queueing.
  if position('?' in v_query) = 0
     and position('؟' in v_query) = 0
     and v_query_norm !~ E'^(من|ما|ماذا|متى|اين|وين|كيف|كم|ليش|لماذا|هل|وش|ايش|اي\\s|who|what|when|where|why|how|is\\s|are\\s|does\\s|do\\s|can\\s)' then
    return new;
  end if;

  if v_query_norm ~ E'(مهم جدا|مهمه جدا|important|ضروري)' then
    v_priority := 'important';
  elsif v_query_norm ~ E'(بسيط|بسيطه|simple)' then
    v_priority := 'simple';
  end if;

  -- Keep the dedupe key compatible with knowledge-gap.ts normalizeGapKey().
  v_key := v_query_norm;
  v_key := regexp_replace(v_key, E'[؟?!.,،؛:;"''`()\\[\\]{}]', ' ', 'g');
  v_key := regexp_replace(v_key, E'\\s+', ' ', 'g');
  v_key := btrim(v_key);

  perform 1
    from public.h_enqueue_knowledge_gap(
      new.user_key,
      encode(extensions.digest(v_key, 'sha256'), 'hex'),
      v_query,
      v_reason,
      v_priority
    );

  return new;
end;
$$;

revoke all on function public.h_capture_chat_knowledge_gap() from public, anon, authenticated;

drop trigger if exists h_runtime_chat_knowledge_gap_capture on public.h_runtime_chat;
create trigger h_runtime_chat_knowledge_gap_capture
after insert on public.h_runtime_chat
for each row
when (new.role = 'assistant')
execute function public.h_capture_chat_knowledge_gap();

comment on function public.h_capture_chat_knowledge_gap() is
  'Conservatively queues stable factual questions after H emits explicit uncertainty; excludes secrets, actions, volatile facts and reply content.';
