// Coach UpRep : relais entre l'appli et l'API Claude (Philippe, 14 novembre 2026 : « L'idée serait qu'il y ait une vraie discussion
// entre l'application et l'utilisateur », « Et que de cette discussion découle les séances adaptées »).
// La clé ANTHROPIC_API_KEY est un secret de la fonction (Supabase → Edge Functions → Secrets) : elle n'est jamais dans l'appli.
import { createClient } from "jsr:@supabase/supabase-js@2";

const MODEL = "claude-haiku-5-5";
const PER_DAY = 40; // messages par jour et par compte
const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, apikey, content-type" };
const out = (o: unknown, status = 200) => new Response(JSON.stringify(o), { status, headers: { ...cors, "Content-Type": "application/json" } });

// réponse structurée : texte, réponses rapides, et éventuellement un plan d'adaptation que l'appli applique après accord
const TOOL = {
  name: "repondre",
  description: "Répondre à l'utilisateur, proposer des réponses rapides et, si utile, un plan d'adaptation de la séance du jour.",
  input_schema: {
    type: "object",
    properties: {
      texte: { type: "string", description: "Message au tutoiement, court (3 phrases maximum), chaleureux et concret." },
      choix: { type: "array", items: { type: "string" }, maxItems: 4, description: "0 à 4 réponses rapides de 1 à 4 mots." },
      plan: {
        type: "object",
        description: "Seulement quand tu proposes d'adapter la séance du jour. Chaque champ est facultatif.",
        properties: {
          seance: { type: "string", description: "Lettre d'une séance disponible (contexte.seances)." },
          repos: { type: "boolean", description: "Proposer un jour de repos à la place de la séance." },
          duree_max: { type: "integer", minimum: 15, maximum: 90, description: "Durée maximale du jour, en minutes." },
          series_moins: { type: "integer", minimum: 0, maximum: 2, description: "Séries retirées à chaque exercice (jamais sous 1 série)." },
          remplacements: { type: "array", items: { type: "object", properties: { de: { type: "string" }, vers: { type: "string" } }, required: ["de", "vers"] },
            description: "Uniquement parmi contexte.exercices[].alternatives (identifiants)." },
          charges: { type: "array", items: { type: "object", properties: { id: { type: "string" }, sens: { type: "integer", enum: [-1, 1] } }, required: ["id", "sens"] },
            description: "Après une séance : un cran de charge en plus (1) ou en moins (-1) pour la prochaine fois, par identifiant d'exercice de dernieres_seances[0]." },
          gene: { type: "object", properties: { zone: { type: "string", enum: ["genou", "cheville", "hanche", "epaule", "coude", "dos"] }, jours: { type: "integer", minimum: 1, maximum: 14 } },
            required: ["zone", "jours"], description: "Gêne qui dure : l'appli ménage cette zone pendant ce nombre de jours (exercices remplacés automatiquement)." },
          sans_materiel_jours: { type: "integer", minimum: 1, maximum: 21, description: "Vacances ou déplacement : séances au poids du corps pendant ce nombre de jours." },
          explications: { type: "array", items: { type: "string" }, description: "Une phrase courte par changement, dans l'ordre." },
        },
        required: ["explications"],
      },
    },
    required: ["texte"],
  },
};

const MODES: Record<string, string> = {
  avant: "Moment : l'utilisateur fait le point avant sa séance. Propose un plan adapté à ses réponses.",
  apres: "Moment : la séance vient de se terminer (dernieres_seances[0]). Félicite brièvement, tiens compte du ressenti et de la réserve ; propose si utile plan.charges pour la prochaine fois (exercices trop faciles : +1, trop durs ou en échec : -1). N'utilise pas les autres champs du plan.",
  libre: "Moment : conversation libre. Réponds à la question ; propose un plan seulement si l'utilisateur demande un changement.",
  suggestion: "Moment : suggestion du jour affichée en haut de l'écran Séance. Message libre, de la longueur que tu juges utile (souvent une à trois phrases), pour aujourd'hui (récupération, pas, activité, régularité), ou un texte vide s'il n'y a rien de pertinent. Pas de plan, pas de choix.",
  bilan: "Moment : bilan de la semaine. Trois phrases maximum : ce qui va bien, l'équilibre des muscles (equilibre_4_semaines, en disant ce que le programme rattrape déjà cette semaine), un ou deux conseils concrets pour la semaine suivante. Pas de plan, pas de choix.",
};

const SYSTEM = (ctx: unknown, lang: string, mode: string) => `Tu es le coach de l'appli UpRep (remise en forme à la maison).
Langue : ${lang === "en" ? "anglais" : "français, tutoiement"}.
Tu discutes avec l'utilisateur pour adapter SA séance du jour et lui donner des conseils simples. Tu t'appuies uniquement sur le contexte ci-dessous.
Règles :
- Messages courts, concrets, encourageants, sans jargon. Pas d'émoji sauf rarement.
- Tu n'inventes pas d'exercice : les remplacements se choisissent dans contexte.exercices[].alternatives ; les séances dans contexte.seances.
- Tu n'établis aucun diagnostic. Douleur vive, gonflement, douleur dans la poitrine, essoufflement anormal, malaise : conseille d'arrêter et de consulter un médecin, sans plan d'entraînement.
- Gêne légère à une articulation : propose des remplacements qui la ménagent et/ou moins de séries.
- Fatigue, mauvaise nuit : moins de séries ou une séance qui ménage les muscles peu récupérés (contexte.recuperation, en %). Muscles « surcharges » : évite les séances qui les travaillent.
- Peu de temps : duree_max.
- Quand tu proposes un plan, l'appli l'affiche avec tes explications et l'utilisateur l'accepte ou non : ne dis pas que c'est déjà fait.
- Gêne qui dure plusieurs jours : plan.gene. Vacances ou déplacement sans matériel : plan.sans_materiel_jours (les séances sont recomposées au poids du corps). Séance express : duree_max.
- Les changements de charge (plan.charges) se font seulement après une séance, d'un cran, en t'appuyant sur le ressenti et la réserve (répétitions encore possibles).
- Réponds toujours avec l'outil « repondre ».
${MODES[mode] ?? ""}
Contexte (JSON) : ${JSON.stringify(ctx)}`;

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  const key = Deno.env.get("ANTHROPIC_API_KEY");
  if (!key) return out({ erreur: "cle" }, 500);
  const sb = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!,
    { global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } } });
  const { data: { user } } = await sb.auth.getUser();
  if (!user) return out({ erreur: "compte" }, 401);
  const { data: n, error } = await sb.rpc("coach_tick");
  if (error) return out({ erreur: "quota_indispo" }, 500);
  if ((n as number) > PER_DAY) return out({ erreur: "quota" }, 429);

  let body: { messages?: { role: string; content: string }[]; context?: unknown; lang?: string; mode?: string };
  try { body = await req.json(); } catch { return out({ erreur: "requete" }, 400); }
  const messages = (body.messages ?? []).slice(-20).filter((m) => (m.role === "user" || m.role === "assistant") && typeof m.content === "string" && m.content.length <= 2000);
  if (!messages.length || messages[0].role !== "user") return out({ erreur: "requete" }, 400);

  const r = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: { "x-api-key": key, "anthropic-version": "2023-06-01", "content-type": "application/json" },
    body: JSON.stringify({ model: MODEL, max_tokens: 900, system: SYSTEM(body.context ?? {}, body.lang ?? "fr", body.mode ?? "avant"), tools: [TOOL],
      tool_choice: { type: "tool", name: "repondre" }, messages }),
  });
  if (!r.ok) return out({ erreur: "api", statut: r.status }, 502);
  const j = await r.json();
  const tu = (j.content ?? []).find((c: { type: string }) => c.type === "tool_use");
  return out(tu?.input ?? { texte: "" });
});
