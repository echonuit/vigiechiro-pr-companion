# M-Paquet - Le paquet de reprise

> **Type** : assistant, ouvert depuis la sélection de [M-MultiSite](M-MultiSite.md) ; sa reprise passe par [M-Import](M-Import.md).
> **Persona principal** : [Samuel](../Personas/Samuel.md) (poste de terrain, poste de bureau) et [Karim](../Personas/Karim.md) (confier une nuit).
> **Parcours couvert** : [P17 - Reprendre une nuit sur un autre poste](../Parcours%20utilisateurs/P17%20-%20Reprendre%20une%20nuit%20sur%20un%20autre%20poste.md).
> **Issue** : #3848. **Cible non livrée, et périmètre non tranché** : les trois questions ci-dessous précèdent toute écriture de code.

Entre la sauvegarde complète, qui porte l'installation entière, et l'export d'observations avec leurs
sons, qui se relit sans se reprendre, il n'existe rien à la maille de **la nuit**.

!!! danger "Cette maquette dessine une hypothèse, pas une décision"
    Elle suppose la réponse « **copie** » à la première question du chantier, parce qu'il faut bien
    dessiner quelque chose. Si le chantier tranche « transfert », l'écran change : il faut alors une
    remise, un état « emportée » sur la nuit d'origine, et l'assistant ci-dessous est faux.

## Maquette principale - préparer le paquet

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 400" role="img" aria-label="Maquette M-Paquet - assistant de préparation d'un paquet de reprise, avec choix du contenu et estimation de volume" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #f7f9fb;">
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
    .warnbox { fill: #fbf1da; stroke: #d9b45a; stroke-width: 1; }
  </style>

  <rect x="0" y="0" width="1000" height="400" fill="#dfe4ea"/>
  <rect class="modal" x="140" y="20" width="720" height="360" rx="6"/>
  <rect class="modal-head" x="140" y="20" width="720" height="38" rx="6"/>
  <text class="titre" x="164" y="45">Préparer un paquet de reprise</text>

  <text class="cell-b" x="164" y="86">3 nuits sélectionnées</text>
  <text class="muted" x="164" y="104">Carré 640380 · points A1, A2 et B1 · passages n° 2</text>

  <text class="head-txt" x="164" y="140">CE QUE LE PAQUET CONTIENDRA</text>
  <line x1="164" y1="148" x2="836" y2="148" stroke="#e3e8ee"/>

  <rect class="box" x="166" y="160" width="13" height="13" rx="2" fill="#3f51b5" stroke="#2c3a8c"/>
  <text class="cell" x="192" y="171">Séquences transformées</text>
  <text class="muted" x="440" y="171">1 626 fichiers, nécessaires à l'écoute</text>
  <text class="num" x="820" y="171">12,9 Go</text>

  <rect class="box" x="166" y="192" width="13" height="13" rx="2"/>
  <text class="cell" x="192" y="203">Enregistrements bruts</text>
  <text class="muted" x="440" y="203">nécessaires pour redéposer la nuit ailleurs</text>
  <text class="num" x="820" y="203">41,2 Go</text>

  <rect class="box" x="166" y="224" width="13" height="13" rx="2" fill="#3f51b5" stroke="#2c3a8c"/>
  <text class="cell" x="192" y="235">Observations, verdicts et validations</text>
  <text class="muted" x="440" y="235">tout ce qui a été saisi sur ces nuits</text>
  <text class="num" x="820" y="235">3,1 Mo</text>

  <rect class="box" x="166" y="256" width="13" height="13" rx="2"/>
  <text class="cell" x="192" y="267">Identifiants de la participation</text>
  <text class="muted" x="440" y="267">permet de poursuivre le dépôt depuis l'autre poste</text>

  <rect class="warnbox" x="164" y="286" width="672" height="34" rx="4"/>
  <text class="muted" x="176" y="300">Les identifiants renvoient à VOTRE compte Vigie-Chiro. Ne les incluez pas dans un paquet</text>
  <text class="muted" x="176" y="314">destiné à quelqu'un d'autre : il déposerait en votre nom.</text>

  <line x1="164" y1="336" x2="836" y2="336" stroke="#e3e8ee"/>
  <text class="cell-b" x="164" y="360">Taille estimée du paquet</text>
  <text class="num" x="470" y="360">12,9 Go</text>
  <text class="muted" x="486" y="360">· 128 Go libres sur la destination</text>

  <rect class="btn" x="606" y="344" width="96" height="26" rx="4"/>
  <text class="btn-txt" x="654" y="361">Annuler</text>
  <rect class="btn-pri" x="714" y="344" width="122" height="26" rx="4"/>
  <text class="btn-pri-txt" x="775" y="361">Préparer…</text>
</svg>
</div>

### Annotations

**Le volume est annoncé avant d'écrire.** Un paquet de reprise se compte en dizaines de gigaoctets.
Une estimation affichée après coup ne sert à rien, et une clé pleine à mi-chemin laisse un fichier
inutilisable. La destination est interrogée en même temps.

**Chaque case dit à quoi elle sert, pas ce qu'elle est.** « Enregistrements bruts, nécessaires pour
redéposer la nuit ailleurs » aide à décider ; « fichiers WAV d'origine » ne le fait pas. C'est la
même règle que sur les autres écrans de décision du produit.

**Les identifiants de participation sont un choix, décoché par défaut, et l'écran dit pourquoi.**
C'est le seul contenu du paquet qui expose le compte de celui qui le fabrique. Un défaut à
« inclus » ferait déposer un collègue au nom de Samuel sans que personne l'ait voulu.

## Interactions clés

| Élément | Action | Effet |
|---|---|---|
| **Case de contenu** | clic | recalcule l'estimation de volume immédiatement. |
| **Identifiants de la participation** | coche | affiche l'avertissement en clair. Décoché par défaut. |
| **Préparer…** | clic | demande la destination, puis produit un fichier unique horodaté, avec progression et annulation. |
| **Volume insuffisant** | à la destination | l'assistant refuse et dit ce qui manque, avant d'écrire le premier octet. |

## La reprise, côté destinataire

Elle passe par [M-Import](M-Import.md), qui accepte déjà un dossier, une archive ZIP et le
glisser-déposer : un paquet est une **source de plus**, pas un assistant de plus. Deux exigences s'y
ajoutent :

- **le contenu est vérifié avant intégration**, comme le fait la restauration ;
- **la nuit déjà présente** sur le poste destinataire a un comportement défini et annoncé avant
  l'écriture. C'est le cas que la maquette ne peut pas dessiner tant que la première question du
  chantier n'est pas tranchée.

## Notes pour l'implémentation

- Les briques existent : la préparation du dépôt compacte déjà des nuits entières, l'export de sons
  produit déjà une archive filtrée, la restauration vérifie déjà un contenu avant bascule.
- Le lot 0 du chantier rend une **décision d'architecture**, pas du code. Cette maquette lui sert
  d'appui : elle montre ce que l'hypothèse « copie » donnerait, pour que le coût de l'autre branche
  se discute sur du concret.
