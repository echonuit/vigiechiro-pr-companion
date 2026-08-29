---
type: adr
title: "Pour ne rien effacer, il faut se souvenir de ce qu'on avait vu"
status: stable
article: A17
chantier: "#4640"
decided_at: 2026-08-29
verification: humaine
verification_note: "aucun garde ne tient encore cette décision : le lot 0 rend un document et le relevé n'existe pas. Le lot 1 apportera le test déterministe qui montre l'envoi partir quand notre valeur égale la base, et renoncer quand les deux côtés ont bougé"
relations:
  prolonge: ["0020-ecrire-sur-la-plateforme-ne-rien-inventer-ni-effacer"]
verified:
  - by: human:nedseb
    at: 2026-08-29
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-29
---

# Pour ne rien effacer, il faut se souvenir de ce qu'on avait vu

## Contexte

L'[ADR 0020](0020-ecrire-sur-la-plateforme-ne-rien-inventer-ni-effacer.md) pose de ne rien effacer, et
l'obtient pour le dictionnaire `configuration` : l'écriture part de l'état distant relu, nos clés s'y
superposent, le reste survit. Les autres champs synchronisables n'ont jamais reçu ce traitement.

Deux mesures l'ont montré, et la seconde renverse ce qu'on croyait avoir livré.

**La plateforme ne protège rien.** Elle ignore l'`If-Match` sur la route des participations : `200`
sans en-tête comme avec un étiquetage faux, mesuré le 2026-08-26. Deux routes d'écriture sur
vingt-neuf posent un `if_match` côté socle, et aucune n'est la nôtre.

**La garde posée en réponse (#4552, #4603) surveille la mauvaise fenêtre.** Ses deux lectures sont
dans le même appel, séparées de quelques millisecondes : la première a lieu au clic sur envoyer, pas
à l'ouverture de l'écran. Elle protège d'une course étroite, jamais du cas où deux postes se relaient
à quelques minutes, qui est le besoin réel.

**Et les dates et la météo ne partent pas du distant.** Elles viennent du passage local et remplacent
ce qui est en face, quelle que soit son ancienneté. La non-destruction de l'ADR 0020 s'arrêtait au
dictionnaire.

## Décision

**Un conflit se constate à trois valeurs, et le dépôt n'en a que deux.** La base manque : ce que nous
avions vu la dernière fois. Sans elle, « l'utilisateur a modifié la météo » et « la plateforme a
modifié la météo » sont indiscernables, et c'est pourquoi la garde actuelle s'est rabattue sur la
seule question que deux valeurs permettent, « quelque chose a-t-il bougé pendant mon appel ».

Le dépôt **persiste donc un relevé des champs synchronisables**, tel que la plateforme les portait à
la dernière lecture, et ce relevé sert de base de comparaison.

Il se remplit sur les lectures qui existent déjà, celles du tirage et de l'envoi. **Aucun appel
réseau n'est ajouté.** L'alternative examinée, relever l'étiquette à l'ouverture de la fiche, a été
écartée sur deux mesures : l'ouverture ne lit rien de distant aujourd'hui, donc elle coûterait un
aller-retour à chaque fiche ouverte ; et une étiquette dit qu'un document a bougé sans dire quel
champ ni de quel côté, ce qui rend le refus inexploitable par qui le reçoit.

**Et la règle n'est pas la même pour tous les champs**, parce que la divergence n'y a pas la même
nature :

| Champ | Sa nature | Ce qui l'arbitre |
|---|---|---|
| `date_debut`, `date_fin` | un fait, dont nous tenons la meilleure source : les enregistrements les prouvent | notre valeur prévaut, et le réalignement se dit déjà à l'utilisateur |
| `meteo` | un fait observé, sans preuve locale pour l'arbitrer | si les deux côtés ont bougé, nous ne pouvons pas trancher : cela se montre |
| `configuration` | un fait matériel | l'ADR 0020 s'applique, inchangée |

Chercher une règle unique pour les trois était l'erreur de #4552 et de #4603, et c'est ce que cette
décision corrige.

## Ce que cette décision met en jeu, et qu'il ne faut pas laisser passer

`V22__participation_traitement.sql` porte déjà un relevé horodaté de l'état distant, et son en-tête
pose une limite : *« La vérité reste côté serveur : ce cache ne fait autorité sur rien, il se
contente de se souvenir. »*

Un relevé qui sert de base de conflit **acquiert une autorité** que celui-là s'interdisait. La
décision l'assume et la borne : le relevé ne dit pas ce qui est vrai, il dit ce que nous avions vu.
Il n'est jamais montré à l'utilisateur comme une donnée, et il ne sert qu'à répondre « ce champ
a-t-il changé de notre côté, du leur, ou des deux ». Un relevé absent ou périmé rend la question sans
réponse, et le geste redevient celui d'aujourd'hui.

Cette borne est écrite ici parce qu'un cache dont le rôle s'élargit sans qu'on le dise finit par être
lu comme une source.

## Conséquences

- Une migration ajoute le relevé, et le tirage comme l'envoi l'alimentent.
- Le cas fréquent cesse d'être refusé : si notre valeur égale la base, nous n'avons rien modifié,
  donc nous n'écrasons rien, et l'envoi part.
- Le seul vrai conflit devient celui où **les deux côtés** ont bougé le même champ. Il est rare, et
  c'est le seul qui mérite d'interrompre l'utilisateur.
- Le message d'un refus peut enfin nommer le champ et les deux valeurs, au lieu de dire « la nuit a
  changé ».
- La fenêtre surveillée cesse d'être de quelques millisecondes pour devenir l'intervalle réel entre
  deux lectures, redémarrage de l'application compris.
- Le resserrement envisagé en #4603 sur les cinq clés de configuration perd son objet : à cette
  échelle, la question n'est plus la même.
