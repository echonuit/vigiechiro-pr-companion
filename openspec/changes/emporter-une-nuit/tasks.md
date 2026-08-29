## 1. Ce que le lot 0 doit laisser derrière lui

- [x] 1.1 Les cinq décisions de `design.md` deviennent une ADR de `dev-docs/decisions/`, numérotée par
      l'issue du chantier. **Fait quand** : l'ADR existe, déclare son article et son niveau de
      vérification, et les gardes d'ADR sont verts. Cette note sera archivée, l'ADR non.
      **Fait** : ADR 4517, article A17, vérification `humaine`. Une seule ADR et non cinq : le
      numéro est celui de l'issue, donc une par issue. Elle porte la décision **structurante**,
      celle qu'un lecteur futur pourrait défaire faute d'en connaître la raison ; le détail des
      cinq reste dans `design.md`, qui part à l'archivage sans disparaître.
- [x] 1.2 Le corps de l'EPIC #3848 annonce trois décisions à prendre, et **les trois** sont tranchées,
      non deux comme cette tâche l'annonçait. **Fait quand** : le corps dit ce qui est tranché et
      renvoie à l'ADR, plutôt que de laisser lire des questions résolues.
      **Fait** : la section « Décisions à prendre » est devenue « Décisions tranchées par le lot 0 »,
      renvoie à l'ADR 4517 et à `design.md`, et nomme les cinq issues du découpage.

## 2. Le schéma accueille l'avis d'un relecteur

- [x] 2.1 Rien ne peut ranger le verdict de quelqu'un d'autre sur une séquence. Ajouter
      `verdict_relecteur` et `relecteur_pseudo` à `selection_sequence`, sur le patron additif de V27.
      **Fait quand** : la migration passe sur une base existante sans perte, et un test constate
      qu'une séquence porte les deux verdicts sans que le premier bouge.
- [x] 2.2 Le verdict du passage doit rester dérivé des seuls verdicts de l'expéditeur. **Fait quand** :
      les tests de `AgregationVerdict` sont verts **sans avoir été modifiés**, et un test neuf pose un
      verdict de relecteur divergent puis constate que le verdict du passage n'a pas bougé.

      **Fait** : `V42__avis_de_relecteur.sql`, enregistrée dans `MigrationSchema`, et
      `SelectionDaoTest.avis_de_relecteur_coexiste_avec_le_verdict_de_l_expediteur` (#4624). Pour
      2.2, `ServiceQualificationTest.l_avis_du_relecteur_ne_deplace_pas_le_verdict_du_passage`
      (#4698) : il tombe quand on fait primer l'avis du relecteur dans la dérivation.

## 3. Écrire le paquet

- [x] 3.1 Rien ne sait dire ce que pèserait un paquet avant de l'écrire. Écrire le plan comme une
      fonction pure, sans écriture disque. **Fait quand** : des tests unitaires couvrent le volume
      ventilé par nature de contenu, et un test constate qu'aucun fichier n'existe après un plan.
- [x] 3.2 Le plan est une classe pure : il doit résister à ses mutations. **Fait quand** : PIT a
      tourné sur elle, ses survivants sont lus un par un, et chacun est soit tué par un test neuf,
      soit justifié par écrit.
- [ ] 3.3 Le paquet n'existe pas. L'écrire depuis un plan confirmé. **Fait quand** : un test écrit un
      paquet dans un dossier temporaire, le relit, et retrouve les séquences **de la sélection**, ses
      métadonnées et ses verdicts, **sans les autres séquences de la nuit ni aucun brut**.

      **Fait** : `PlanDePaquetTest` pour 3.1, et PIT sur le plan pour 3.2 (#4625).

## 4. Ouvrir le paquet, et signer ce qu'on y fait

- [x] 4.1 Un paquet ouvert sans identité valide recueillerait des verdicts anonymes. Apposer
      l'identité à l'ouverture, refuser l'ouverture sans elle. **Fait quand** : un test ouvre un
      paquet sans connexion valide et constate un refus qui nomme la cause.
- [x] 4.2 L'identité se périme à quatorze jours alors que le jugement peut venir après. **Fait
      quand** : un test ouvre un paquet avec une identité valide, avance l'horloge injectée au-delà de
      la péremption, pose un verdict, et constate le pseudo relevé à l'ouverture.
- [ ] 4.3 La régénération rouvrirait le problème que D1 ferme : deux échantillons quasi disjoints.
      **Fait quand** : un test constate qu'une nuit venue d'un paquet refuse la régénération, et que
      le refus dit que la sélection est celle de l'expéditeur.

      **Fait, avec sa réserve pour 4.2** : le test éprouve que le pseudo est **capturé** et non relu,
      en deux actes, l'identité disparue étant constatée par un refus d'ouvrir. Il tombe si l'ouverture
      capture l'identifiant de plateforme au lieu du pseudo. Il n'éprouve pas encore la pose d'un
      verdict au travers d'un paquet ouvert après péremption : ce flux n'existe pas, et l'horloge que
      cette tâche décrivait n'aurait servi qu'à ce seul test. La suite est consignée séparément.

## 5. Reprendre l'avis, sans écraser le sien

- [x] 5.1 Un avis importé écraserait le verdict de l'expéditeur. **Fait quand** : un test importe un
      avis divergent et constate que les deux verdicts subsistent, chacun avec son pseudo.
- [x] 5.2 La sélection étant figée, un verdict hors d'elle signale un paquet qui ne correspond pas à
      la nuit. **Fait quand** : un test importe un tel verdict et constate un refus qui nomme la
      séquence en cause, sans que rien n'ait été écrit.
- [ ] 5.3 Un second avis effacerait le premier sans un mot. **Fait quand** : un test importe un avis
      sur une nuit qui en porte déjà un et constate une double confirmation qui nomme le relecteur
      présent et le nombre de verdicts perdus ; un second test refuse la confirmation et constate que
      rien n'a été écrit.

## 6. Ce qui se voit, et ce qui se prouve

- [ ] 6.1 Les deux verdicts doivent se lire côte à côte dans l'écran de qualification. **Fait quand** :
      un test de scénario sur la vue constate les deux verdicts et les deux pseudos sur une séquence
      jugée deux fois.
- [ ] 6.2 Un geste d'écran qu'aucun clip ne montre ne se vérifie que sur parole. **Fait quand** : le
      parcours « emporter une nuit, la relire ailleurs, reprendre l'avis » a son cas de recette et son
      clip, contrôle négatif compris.
- [ ] 6.3 La documentation de l'écran décrit un écran qui aura changé. **Fait quand** : `docs/ecrans/`
      décrit le nouveau geste, ses refus et l'affichage de l'avis d'un relecteur, et le garde de
      documentation à jour est vert.
- [ ] 6.4 Toute capacité métier s'offre aussi en ligne de commande (A19). **Fait quand** : l'emport et
      la reprise ont leur commande, couvertes par `cli-surface.bats` et `cli.bats`, ou une issue dit
      pourquoi elles ne l'ont pas.
