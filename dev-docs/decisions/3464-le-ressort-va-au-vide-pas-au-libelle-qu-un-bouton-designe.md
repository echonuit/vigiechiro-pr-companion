# ADR 3464 - Le ressort va au vide, pas au libellé qu'un bouton désigne

- **Statut** : Accepté - 2026-08-18
- **Chantier** : #3464, finitions de recette avant la campagne 2 (#3424)
- **Vérification** : humaine - aucun test ne voit un bouton loin de son texte ; c'est la revue visuelle
  (passe 8) qui l'attrape, et elle l'a fait **trois fois**. Loupe :
  `.github/assets/apercu-lot-televerser.png` et `apercu-reglages-emplacements.png`

## Contexte

Trois fois dans le même chantier, une rangée `HBox` a mis un contrôle **loin de ce qu'il désigne**, et
trois fois le test associé était vert.

| Écran | Ce qui s'est passé | Comment ça s'est vu |
|---|---|---|
| Lot, étape téléverser (#3464) | « Copier » rejeté à ~500 px du chemin qu'il copie | aperçu régénéré et **regardé** |
| Réglages ▸ Emplacements (#3882) | même configuration, le défaut se serait reproduit à l'identique | prévu, parce que #3464 venait de l'apprendre |
| Bandeau d'annonce (#3876) | le lien « Voir cette version » **abrégé en ellipse**, à 111 px près | le garde anti-troncature, en CI |

Le mécanisme est le même partout : le **libellé** portait `HBox.hgrow="ALWAYS"` et
`maxWidth="Infinity"`. Il prenait donc toute la largeur, et l'`HBox` comprimait ses voisins jusqu'à leur
minimum - ou les repoussait contre le bord.

## Décision

**Dans une rangée, le ressort (`hgrow`) va à un `Region` vide, pas au libellé.**

```xml
<HBox>
  <Label fx:id="chemin" styleClass="chemin" wrapText="true"/>
  <Button fx:id="btnCopierChemin" text="Copier" .../>
  <Region HBox.hgrow="ALWAYS"/>   <!-- c'est LUI qui absorbe la place -->
  <Button fx:id="btnChoisir" text="Choisir…"/>
</HBox>
```

Un contrôle qui **désigne** le texte voisin - « Copier » ce chemin-ci, « Voir » cette version-ci - se
pose **contre** lui. Un contrôle qui ne désigne rien - « Choisir… », qui ouvre un sélecteur - peut vivre
au bord droit.

Corollaire pour un contrôle qui ne doit jamais rétrécir : `minWidth="-Infinity"`, ce que le garde
anti-troncature nomme lui-même dans son message.

## Pourquoi cette règle a besoin d'être écrite

Parce que **le test ne la voit pas**. Sur #3464, le test cliquait le bouton et vérifiait que le
presse-papier recevait le chemin : il passait, avec le bouton à cinq cents pixels de sa cible. La
propriété « ce bouton se lit comme désignant ce texte » n'est pas assertable.

C'est le constat qui a fondé la passe 8 - *« un geste testé n'est pas un écran regardé »* - et cette
ADR en donne la forme concrète pour un cas qui revient : **trois occurrences en un seul chantier**.

⚠️ L'[ADR 3672](3672-deux-cas-ne-font-pas-une-mecanique.md) refuse d'abstraire sur deux cas. Il y en a
trois, et le troisième n'a pas été trouvé en regardant mais **par un garde**, ce qui indique que la
forme du défaut est stable.

## Conséquences

- **La revue visuelle attrape ce que le test manque**, et c'est le seul chemin. Sur #3464, l'aperçu a
  démenti l'intention écrite dans le bloc d'ouverture de l'issue même : « un bouton posé contre ce qu'il
  copie n'a rien à expliquer ».
- **Un contrôle ajouté à une rangée existante rallonge la carte.** Sur #3464, le garde anti-troncature a
  chiffré le manque à **6 px** et refusé la capture ; la scène de `CaptureLot` est passée de 1180 à
  1200. Prendre la marge au-delà du strict nécessaire est délibéré : un aperçu qui tient à six pixels
  près rougira au premier mot ajouté ailleurs.
- ⚠️ **Ce n'est pas une règle de mise en page générale.** Elle ne dit rien des rangées où aucun contrôle
  ne désigne son voisin, ni des grilles. Elle porte sur un cas précis : `hgrow` sur un libellé **suivi
  d'un contrôle qui le désigne**.

## Alternative écartée

**Rendre le libellé sélectionnable plutôt qu'ajouter un bouton** (`TextField` en lecture seule), ce que
#3464 proposait. Mesuré : un `TextField` ne sait pas enrouler. Un chemin Windows profond y défilerait
horizontalement, donc serait **coupé**. On aurait échangé « non copiable » contre « non lisible », et
acheté le défaut que la passe 8 traque en premier.
