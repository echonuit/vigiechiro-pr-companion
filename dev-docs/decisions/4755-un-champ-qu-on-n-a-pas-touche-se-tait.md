---
type: adr
title: "Un champ qu'on n'a pas touché se tait, au lieu d'être arbitré"
status: stable
article: A17
chantier: "#4755, lot 2 de l'EPIC #4640"
decided_at: 2026-08-29
verification: humaine
verification_note: "aucun compteur ne dit quels champs un corps omet : c'est une lecture de code, tenue par les bancs de ResolutionMeteo et par la capture du corps envoyé dans SynchronisationParticipationTest. La loupe de l'ADR 0020 énumère la surface d'écriture à confronter"
loupe:
  - "scripts/adr/loupe-0020-ecritures-plateforme.py"
relations:
  prolonge: ["4640-pour-ne-rien-effacer-il-faut-se-souvenir-de-ce-qu-on-avait-vu"]
verified:
  - by: human:nedseb
    at: 2026-08-29
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-29
---

# Un champ qu'on n'a pas touché se tait, au lieu d'être arbitré

## Contexte

L'ADR 4640 a posé la base : ce que la plateforme portait à notre dernière lecture. Elle permet enfin de
distinguer « l'utilisateur a modifié la météo » de « la plateforme l'a modifiée », et le lot 1 l'a
câblée : `pousserVers` compare contre elle et renonce si elle a bougé.

Ce renoncement s'est révélé trop large dès qu'on l'a regardé de près. Qu'un collègue ait écrit ne dit
rien d'un **désaccord**. S'il saisit la météo pendant que nous ne la touchons pas, il ne nous contredit
en rien : il n'y a rien à arbitrer. L'envoi entier était pourtant refusé, configuration matérielle
comprise, qui n'était pour rien dans l'affaire.

La table par champ de l'ADR 4640 annonçait la difficulté sans la trancher : « `meteo` : un fait observé,
sans preuve locale pour l'arbitrer ; si les deux côtés ont bougé, nous ne pouvons pas trancher ». Elle
disait quoi faire quand **les deux** ont bougé. Elle ne disait pas quoi faire quand **un seul** l'a fait.

## Décision

**La sortie n'est pas de choisir laquelle des trois valeurs envoyer : c'est d'envoyer la nôtre, ou de
taire le champ.**

Une clé absente du corps laisse la plateforme garder la sienne : le GSON de `RequetesVigieChiro` est
construit sans `serializeNulls()`, et un test l'atteste. Le silence devient donc une **réponse**, et
non l'absence de réponse.

| base | nous | eux | ce qui part |
|---|---|---|---|
| M0 | M0 | M1 | rien : leur saisie survit |
| M0 | M1 | M0 | la nôtre |
| M0 | M1 | M1 | rien : le champ porte déjà notre valeur |
| M0 | M1 | M2 | rien du tout : le seul vrai conflit, et l'envoi est refusé |

Le partage devient possible **sans que personne n'arbitre**, ce que trois valeurs seules ne
permettaient pas.

## La condition, et ce qu'elle exclut

Un champ ne peut se taire que si l'**omettre** et le **laisser tel quel** sont la même chose côté
serveur. C'est vrai d'un champ scalaire dans un `PATCH`, et faux d'un dictionnaire que le `PATCH`
remplace en entier : la `configuration` part donc toujours complète, et tout changement depuis la base y
reste un conflit (ADR 0020, inchangée).

La règle ne se lit pas « en cas de doute, on se tait ». Elle se lit : **on n'envoie que ce qu'on a
changé**, et se taire sur le reste est la conséquence, pas l'intention.

## Ce que cette décision empêche

Elle interdit d'écrire un arbitre. Chercher « laquelle des trois est la bonne » demande un critère que
personne ne détient : ni l'application, ni la plateforme, ni l'utilisateur qui n'a pas vu l'autre
saisie. Tout critère inventé aurait perdu du travail dans un des deux sens.

Elle interdit aussi de resserrer le renoncement en le rendant plus **permissif**, envoyer quand même
et laisser le dernier gagner. Le troisième cas de la table reste un refus, et c'est le seul.

## Le repli, qui n'est pas un second mécanisme

Sur une nuit antérieure à la migration V43, il n'y a pas de base. La lecture du haut d'appel **en tient
lieu** : une base d'une milliseconde, qui ne couvre qu'une course étroite. Cela redonne exactement la
garde d'avant le lot 1, sans qu'un second comparateur ait à l'écrire : les deux méthodes qui existaient
fondent en une.

C'est le corollaire à retenir : **un repli se construit en dégradant la valeur, pas en doublant le
mécanisme.** Deux mécanismes divergent ; une valeur dégradée reste comparable.

## Conséquences

- `ResolutionMeteo` porte la table, et ne rend que deux issues : la météo à envoyer, ou le conflit.
- Un bloc météo aux quatre composants absents vaut l'absence de bloc, sans quoi une nuit sans météo des
  deux côtés passerait pour un désaccord et bloquerait la configuration.
- **Effacer** une météo ne s'envoie pas : le corps ne sait pas porter un effacement, faute de
  `serializeNulls()`. Défaut antérieur à cette décision, qu'elle rend seulement visible ; ouvert en
  #4777, et un banc l'atteste.
