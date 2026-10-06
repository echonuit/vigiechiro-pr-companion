# M-Paquet - Le paquet de reprise

> **Type** : quatre entrées du menu ☰ de la liste de [M-Qualification](M-Qualification.md), et les dialogues qu'elles ouvrent. Ce n'est pas un écran à part.
> **Persona principal** : [Karim](../Personas/Karim.md) (confier une nuit, reprendre l'avis rendu).
> **Parcours couvert** : [P17 - Reprendre une nuit sur un autre poste](../Parcours%20utilisateurs/P17%20-%20Reprendre%20une%20nuit%20sur%20un%20autre%20poste.md).
> **Issue** : #3848, livré par son sous-chantier #4628.

Entre la sauvegarde complète, qui porte l'installation entière, et l'export d'observations avec leurs
sons, qui se relit sans se reprendre, il manquait de quoi faire juger **une nuit** par quelqu'un
d'autre et reprendre son avis.

Cette fiche a d'abord dessiné un assistant de préparation, ouvert depuis
[M-MultiSite](M-MultiSite.md) sur plusieurs nuits cochées, avec quatre cases de contenu
(séquences, bruts, observations, identifiants de la participation) et une estimation recalculée à
chaque case. Il a été écarté et n'a jamais été construit : l'[ADR 4517](https://companion-dev.echonuit.fr/decisions/4517-un-avis-de-relecteur-se-range-a-cote/)
et l'[ADR 4627](https://companion-dev.echonuit.fr/decisions/4627-le-paquet-fige-la-selection-d-ecoute/)
ont fixé ce que le paquet contient, si bien qu'il ne restait rien à cocher. La maquette ci-dessous
montre ce que l'écran fait.

## Maquette principale - le menu de la liste, et l'annonce avant d'écrire

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 400" role="img" aria-label="Maquette M-Paquet - le menu de la liste de l'écran de vérification déplié sur ses quatre gestes d'emport, et la confirmation qui annonce le volume avant d'écrire" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #f7f9fb;">
  <style>
    .modal { fill: #ffffff; stroke: #9aa4b2; stroke-width: 1; }
    .modal-head { fill: #eef2f5; stroke: #c4ccd4; stroke-width: 1; }
    .titre { font: 600 14px sans-serif; fill: #2c3e50; }
    .cell { font: 12px sans-serif; fill: #2c3e50; }
    .cell-b { font: 600 12px sans-serif; fill: #2c3e50; }
    .muted { font: 11px sans-serif; fill: #6b7a8d; }
    .head-txt { font: 600 11px sans-serif; fill: #4a6785; }
    .num { font: 600 15px sans-serif; fill: #2c3e50; text-anchor: end; }
    .btn { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .btn-pri { fill: #3f51b5; stroke: #2c3a8c; stroke-width: 1; }
    .btn-txt { font: 600 11px sans-serif; fill: #37405c; text-anchor: middle; }
    .btn-pri-txt { font: 600 11px sans-serif; fill: #ffffff; text-anchor: middle; }
    .box { fill: #ffffff; stroke: #6b7a8d; stroke-width: 1.4; }
    .menu { fill: #ffffff; stroke: #6b7a8d; stroke-width: 1.2; }
    .survol { fill: #e8ebf8; }
    .badge { fill: #e3f1e7; stroke: #8fc19f; stroke-width: 1; }
  </style>

  <rect x="0" y="0" width="1000" height="400" fill="#dfe4ea"/>

  <!-- La colonne de gauche de M-Qualification : titre, boutons, table -->
  <rect class="modal" x="20" y="20" width="560" height="360" rx="6"/>
  <text class="titre" x="38" y="48">📋 Sélection d'écoute (30 séquences)</text>
  <rect class="btn" x="424" y="32" width="96" height="26" rx="4"/>
  <text class="btn-txt" x="472" y="49">Régénérer</text>
  <rect class="btn-pri" x="530" y="32" width="34" height="26" rx="4"/>
  <text class="btn-pri-txt" x="547" y="50">☰</text>

  <line x1="38" y1="72" x2="564" y2="72" stroke="#e3e8ee"/>
  <text class="head-txt" x="38" y="92">N°</text>
  <text class="head-txt" x="62" y="92">FICHIER</text>
  <text class="head-txt" x="188" y="92">VERDICT</text>
  <text class="head-txt" x="250" y="92">AVIS RELECTEUR</text>
  <line x1="38" y1="100" x2="564" y2="100" stroke="#e3e8ee"/>

  <text class="cell" x="38" y="122">1</text>
  <text class="cell" x="62" y="122">…221504_000.wav</text>
  <text class="cell" x="188" y="122">Bon</text>
  <rect class="badge" x="248" y="108" width="92" height="20" rx="10"/>
  <text class="cell" x="256" y="122">Bon · lucie.r</text>

  <text class="cell" x="38" y="150">2</text>
  <text class="cell" x="62" y="150">…223011_000.wav</text>
  <text class="cell" x="188" y="150">Mauvais</text>
  <rect class="badge" x="248" y="136" width="92" height="20" rx="10"/>
  <text class="cell" x="256" y="150">Bon · lucie.r</text>

  <text class="cell" x="38" y="178">3</text>
  <text class="cell" x="62" y="178">…230447_000.wav</text>
  <text class="cell" x="188" y="178">Non jugé</text>

  <text class="muted" x="38" y="364">Une séquence que le relecteur n'a pas jugée n'affiche rien dans sa colonne.</text>

  <!-- Le menu ☰ déplié -->
  <rect class="menu" x="352" y="60" width="212" height="160" rx="4"/>
  <text class="cell" x="366" y="82">Colonnes…</text>
  <line x1="352" y1="92" x2="564" y2="92" stroke="#c4ccd4"/>
  <rect class="survol" x="353" y="96" width="210" height="26"/>
  <text class="cell-b" x="366" y="114">Emporter cette nuit…</text>
  <text class="cell" x="366" y="142">Ouvrir un paquet reçu…</text>
  <line x1="352" y1="154" x2="564" y2="154" stroke="#c4ccd4"/>
  <text class="cell" x="366" y="178">Renvoyer mon avis…</text>
  <text class="cell" x="366" y="206">Reprendre un avis reçu…</text>

  <!-- La confirmation, après le choix du fichier -->
  <rect class="modal" x="610" y="96" width="370" height="170" rx="6"/>
  <rect class="modal-head" x="610" y="96" width="370" height="34" rx="6"/>
  <text class="cell" x="626" y="158">30 séquence(s) seront écrites, soit 148480 Ko</text>
  <text class="cell" x="626" y="178">(dont 148478 Ko d'audio). Emporter cette nuit ?</text>
  <rect class="btn-pri" x="760" y="222" width="100" height="26" rx="4"/>
  <text class="btn-pri-txt" x="810" y="239">Confirmer</text>
  <rect class="btn" x="870" y="222" width="94" height="26" rx="4"/>
  <text class="btn-txt" x="917" y="239">Annuler</text>
  <text class="muted" x="610" y="290">Elle s'ouvre après le choix du fichier à écrire,</text>
  <text class="muted" x="610" y="306">et rien n'est écrit avant « Confirmer ».</text>
</svg>
</div>

### Annotations

**Les gestes sont dans le menu, pas dans la barre.** Ils sont rares, et quatre boutons de plus
feraient reculer ceux qu'on emploie à chaque nuit. Un séparateur les range en deux groupes : les deux
premiers pour le paquet de la nuit, qui part puis s'ouvre, les deux suivants pour l'avis, qui repart
puis se reprend.

**Le volume est annoncé avant d'écrire.** La confirmation donne le nombre de séquences, le volume
total et la part d'audio, en octets sous le kilooctet et en kilooctets au-delà. L'écriture reprend
exactement ce qui a été annoncé : le plan n'est pas recalculé entre l'accord et l'archive.

**L'avis se lit sur la ligne de la séquence.** La colonne « Avis relecteur » porte le verdict du
relecteur suivi de son pseudo, à côté de la colonne « Verdict » qui reste celle de l'utilisateur du
poste. Elle reste toujours affichée : « Colonnes… » ne propose pas de la masquer.

## Interactions clés

| Élément | Action | Effet |
|---|---|---|
| **Emporter cette nuit…** | clic | sélecteur d'enregistrement (nom proposé `nuit.zip`), puis la confirmation ci-dessus, puis le compte rendu « Nuit emportée ». Une nuit sans sélection d'écoute est refusée avec le motif. |
| **Ouvrir un paquet reçu…** | clic | sélecteur de fichier, puis une confirmation qui annonce le remplacement de la sélection de cette nuit et la perte des verdicts posés ici, puis « Paquet ouvert » avec le nombre de séquences et le pseudo relevé. La liste se recharge. |
| **Renvoyer mon avis…** | clic | sélecteur d'enregistrement (nom proposé `avis.zip`), puis « Avis renvoyé » avec le nombre de verdicts et le pseudo qui signe. Sans connexion, « Avis non renvoyé » le dit et rien n'est écrit. |
| **Reprendre un avis reçu…** | clic | sélecteur de fichier, puis « Avis repris ». La confirmation n'est demandée que si un avis est déjà rangé : elle nomme son auteur et compte les verdicts qui seraient remplacés. La liste se recharge. |
| **Régénérer** | clic, sur une sélection reçue d'un paquet | refusé avec le motif : la sélection est celle de l'expéditeur, et elle est figée. |

Un refus du service (nuit inconnue du poste, séquence absente, paquet sans manifeste, avis non signé)
arrive dans le même compte rendu, en avertissement, avec la phrase du service.

## Ce que la maquette ne montre pas, parce que ce n'est pas construit

- **Aucun choix de contenu.** Le paquet emporte les séquences de la sélection d'écoute et leurs
  verdicts. Les bruts et les identifiants de la plateforme n'y entrent jamais.
- **Une nuit à la fois**, depuis l'écran de vérification de cette nuit. Il n'y a pas d'emport de
  plusieurs nuits depuis « Carte & passages ».
- **La place libre à la destination n'est pas interrogée.** L'annonce dit ce que le paquet pèsera,
  pas ce qui reste sur la clé.
- **Le nom proposé n'est pas daté.** La question est posée au porteur, voir
  [P17](../Parcours%20utilisateurs/P17%20-%20Reprendre%20une%20nuit%20sur%20un%20autre%20poste.md).
- **La reprise ne passe pas par [M-Import](M-Import.md).** Ouvrir un paquet n'installe pas une nuit :
  le poste doit déjà la détenir. Le trajet du terrain au bureau est sorti du chantier, vers l'issue
  de cadrage #6043.

## Notes pour l'implémentation

- Les quatre gestes vivent hors du contrôleur de l'écran, dans `GestesEmportQualification` et
  `ActionsEmport`. Sélecteur, confirmation et compte rendu y arrivent en porteurs remplaçables, ce qui
  rend chaque geste jouable sans fenêtre.
- Le service `ServiceEmport` sépare préparer d'écrire, pour l'emport comme pour la reprise d'un avis :
  l'écran lit le plan pour savoir quoi annoncer et s'il doit demander un accord.
- Chaque geste a sa commande (`emporter-nuit`, `ouvrir-paquet-recu`, `renvoyer-avis`,
  `reprendre-avis`), décrite dans P17.
