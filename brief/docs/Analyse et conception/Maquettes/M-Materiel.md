# M-Materiel - Le parc d'enregistreurs et de micros

> **Type** : onglet **« Matériel »** des Réglages. Première fiche de maquette portant sur cet écran, qui n'en avait aucune.
> **Persona principal** : [Karim](../Personas/Karim.md) (un parc qui tourne sur plusieurs chantiers).
> **Parcours couvert** : [P16 - Déclarer et retrouver son matériel](../Parcours%20utilisateurs/P16%20-%20Déclarer%20et%20retrouver%20son%20matériel.md).
> **Issue** : #3847. **Cible non livrée** : l'écran n'existe pas, son état se lit sur l'issue.
> **Concepts** : [C4 - Enregistreur](../Modèle%20conceptuel/C4%20-%20Enregistreur.md), [C4bis - Micro](../Modèle%20conceptuel/C4bis%20-%20Micro.md).

Le modèle conceptuel décrit ce parc depuis l'origine, et la base le tient. L'import lit le numéro de
série dans le journal du capteur, si bien que **les enregistreurs sont déjà là** quand on ouvre cet
écran pour la première fois. Ce que le chantier ajoute n'est pas la donnée : c'est le fait de la voir,
de la corriger, et de déclarer le micro monté dessus.

> **Un écran qui ne part pas d'une page blanche.** Le premier affichage montre ce que les imports ont
> accumulé. C'est la différence avec un formulaire de déclaration ordinaire, et cela change le premier
> geste attendu : corriger et compléter, plutôt que saisir.

## Maquette principale - le parc, et le micro monté

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 420" role="img" aria-label="Maquette M-Materiel - liste des enregistreurs du parc et détail du micro monté sur celui qui est sélectionné" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #f7f9fb;">
  <style>
    .chrome { fill: #3f51b5; }
    .chrometxt { fill: #ffffff; font: 600 14px sans-serif; }
    .crumb { fill: #c5cae9; font: 13px sans-serif; }
    .crumb-curr { fill: #ffffff; font: 700 13px sans-serif; }
    .card { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .head { fill: #eef2f5; stroke: #c4ccd4; stroke-width: 1; }
    .head-txt { font: 600 11px sans-serif; fill: #4a6785; }
    .titre { font: 600 13px sans-serif; fill: #2c3e50; }
    .cell { font: 12px sans-serif; fill: #2c3e50; }
    .cell-b { font: 600 12px sans-serif; fill: #2c3e50; }
    .muted { font: 11px sans-serif; fill: #6b7a8d; }
    .sel { fill: #e8ebfb; }
    .tab { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .tab-on { fill: #4a90d9; stroke: #2563a3; stroke-width: 1; }
    .tab-txt { font: 12px sans-serif; fill: #2c3e50; text-anchor: middle; }
    .tab-on-txt { font: 600 12px sans-serif; fill: #ffffff; text-anchor: middle; }
    .btn { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .btn-pri { fill: #3f51b5; stroke: #2c3a8c; stroke-width: 1; }
    .btn-txt { font: 600 11px sans-serif; fill: #37405c; text-anchor: middle; }
    .btn-pri-txt { font: 600 11px sans-serif; fill: #ffffff; text-anchor: middle; }
    .chip { font: 600 10px sans-serif; text-anchor: middle; }
    .field { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
  </style>

  <rect x="0" y="0" width="1000" height="420" fill="#f7f9fb"/>

  <rect class="chrome" x="0" y="0" width="1000" height="34"/>
  <text class="chrometxt" x="16" y="22">VigieChiro Companion</text>
  <text class="crumb" x="200" y="22">Accueil ›</text>
  <text class="crumb-curr" x="262" y="22">Réglages</text>

  <rect class="tab" x="16" y="48" width="110" height="26" rx="4"/>
  <text class="tab-txt" x="71" y="66">Général</text>
  <rect class="tab" x="132" y="48" width="120" height="26" rx="4"/>
  <text class="tab-txt" x="192" y="66">Emplacements</text>
  <rect class="tab-on" x="258" y="48" width="110" height="26" rx="4"/>
  <text class="tab-on-txt" x="313" y="66">Matériel</text>
  <rect class="tab" x="374" y="48" width="132" height="26" rx="4"/>
  <text class="tab-txt" x="440" y="66">Fonctionnalités</text>

  <text class="muted" x="16" y="98">3 enregistreurs, reconnus dans vos journaux de capteur depuis le 12/05/2026.</text>
  <rect class="btn" x="736" y="86" width="120" height="24" rx="4"/>
  <text class="btn-txt" x="796" y="102">Exporter le parc</text>
  <rect class="btn" x="868" y="86" width="116" height="24" rx="4"/>
  <text class="btn-txt" x="926" y="102">Importer…</text>

  <rect class="head" x="16" y="122" width="450" height="26"/>
  <text class="head-txt" x="28" y="139">N° DE SÉRIE</text>
  <text class="head-txt" x="180" y="139">MODÈLE</text>
  <text class="head-txt" x="330" y="139">NUITS</text>
  <text class="head-txt" x="392" y="139">MICRO</text>
  <rect class="card" x="16" y="148" width="450" height="130"/>

  <rect class="sel" x="17" y="149" width="448" height="42"/>
  <text class="cell-b" x="28" y="166">1925492</text>
  <text class="muted" x="28" y="182">retour SAV avril, membrane remplacée</text>
  <text class="cell" x="180" y="166">Teensy V1.01</text>
  <text class="cell" x="330" y="166">14</text>
  <rect x="386" y="157" width="68" height="17" rx="8" fill="#e6f4ec"/>
  <text class="chip" fill="#1e8449" x="420" y="169">déclaré</text>

  <line x1="17" y1="191" x2="465" y2="191" stroke="#e3e8ee"/>
  <text class="cell-b" x="28" y="212">1925488</text>
  <text class="cell" x="180" y="212">Teensy V1.01</text>
  <text class="cell" x="330" y="212">9</text>
  <rect x="386" y="203" width="68" height="17" rx="8" fill="#fbf1da"/>
  <text class="chip" fill="#b7791f" x="420" y="215">à déclarer</text>

  <line x1="17" y1="234" x2="465" y2="234" stroke="#e3e8ee"/>
  <text class="cell-b" x="28" y="255">2MU08078</text>
  <text class="cell" x="180" y="255">Teensy T4.1</text>
  <text class="cell" x="330" y="255">3</text>
  <rect x="386" y="246" width="68" height="17" rx="8" fill="#fbf1da"/>
  <text class="chip" fill="#b7791f" x="420" y="258">à déclarer</text>

  <rect class="head" x="482" y="122" width="502" height="26"/>
  <text class="head-txt" x="494" y="139">ENREGISTREUR 1925492 · MICRO MONTÉ</text>
  <rect class="card" x="482" y="148" width="502" height="130"/>
  <text class="cell" x="494" y="172">Modèle</text>
  <rect class="field" x="620" y="158" width="240" height="22" rx="3"/>
  <text class="cell" x="630" y="173">FG black</text>
  <text class="cell" x="494" y="204">Mis en service le</text>
  <rect class="field" x="620" y="190" width="140" height="22" rx="3"/>
  <text class="cell" x="630" y="205">14/06/2026</text>
  <text class="cell" x="494" y="236">Hauteur habituelle</text>
  <rect class="field" x="620" y="222" width="90" height="22" rx="3"/>
  <text class="cell" x="630" y="237">4,0 m</text>
  <text class="cell" x="494" y="268">Position</text>
  <rect class="field" x="620" y="254" width="140" height="22" rx="3"/>
  <text class="cell" x="630" y="269">Sol</text>
  <rect class="btn" x="876" y="254" width="96" height="22" rx="4"/>
  <text class="btn-txt" x="924" y="269">Changer…</text>

  <rect class="head" x="482" y="294" width="502" height="26"/>
  <text class="head-txt" x="494" y="311">MICROS PRÉCÉDENTS</text>
  <rect class="card" x="482" y="320" width="502" height="80"/>
  <text class="cell" x="494" y="344">FG black</text>
  <text class="muted" x="620" y="344">du 12/05/2026 au 14/06/2026 · 8 nuits</text>
  <text class="muted" x="494" y="368">Un micro retiré reste attaché aux nuits qu'il a enregistrées.</text>
  <text class="muted" x="494" y="388">Il ne réapparaît plus dans les listes de saisie.</text>
</svg>
</div>

### Annotations

**La colonne « Nuits » justifie la ligne.** Un enregistreur qui a produit quatorze nuits n'est pas une
saisie administrative : c'est l'identité qui relie ces quatorze nuits entre elles. La colonne rend
visible ce que C4 annonce et que rien ne montrait, à savoir le suivi du matériel dans le temps.

**« À déclarer » n'est pas une erreur.** Un enregistreur reconnu par l'import mais dont le micro n'a
pas été déclaré fonctionne parfaitement ; l'étiquette signale une information manquante, pas un
défaut. Le vocabulaire compte : « incomplet » se lirait comme un reproche.

**Le micro se change, il ne s'écrase pas.** Le bouton « Changer… » ouvre la déclaration du nouveau
micro et met le précédent à l'écart avec sa date de retrait. Les tables portent déjà ce mécanisme.
C'est ce qui permet, plus tard, d'expliquer une différence de qualité acoustique entre deux séries de
nuits du même point.

**Le parc s'exporte.** Équiper un second poste ne doit pas se payer d'une ressaisie, et un parc
ressaisi à la main est un parc qui diverge.

## Interactions clés

| Élément | Action | Effet |
|---|---|---|
| **Ligne d'enregistreur** | clic | charge le panneau de droite sur cet enregistreur. |
| **Champ du panneau** | saisie | modifie la déclaration. Rien n'est écrit sur les passages passés. |
| **Changer…** | clic | déclare un nouveau micro et met le précédent à l'écart, à une date demandée. |
| **Exporter le parc / Importer…** | clic | fichier d'échange entre deux postes. Un import fusionne sur le numéro de série. |
| **Suppression d'un enregistreur** | - | **à trancher au chantier** : la clé est citée par des passages. Interdire, mettre à l'écart comme un micro, ou conserver le texte. |

## Variantes attendues

- **Parc vide** : aucune installation n'a encore importé de nuit. L'écran l'explique et renvoie vers
  l'import, plutôt que d'offrir un formulaire de création qui serait le mauvais premier geste.
- **Conflit à l'import du parc** : un numéro de série présent des deux côtés avec des modèles
  différents. L'écran demande lequel garder, il ne choisit pas.

## Notes pour l'implémentation

- Les tables `recorder` et `microphone` existent, avec la mise à l'écart d'un micro déjà prévue. Le
  code d'accès aux micros est écrit et testé, et n'est appelé par aucun écran : c'est ce qui rend ce
  chantier court.
- **La décision de fond** est ailleurs : l'import écrase aujourd'hui le modèle et le commentaire d'un
  enregistreur déjà connu, à chaque nuit. Tant qu'elle n'est pas prise, tout ce que l'utilisateur
  saisit dans cet écran peut être effacé par l'import suivant, ce qui rendrait l'écran trompeur.
- Les valeurs par défaut du micro alimentent la saisie d'un passage ([M-Passage](M-Passage.md)) sans
  la contraindre : ce qui est prérempli reste modifiable pour cette nuit-là.
