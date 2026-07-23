# ADR 0020 — Écrire sur la plateforme : ne rien inventer, ne rien effacer

- **Statut** : Accepté — 2026-07-18
- **Chantier** : #1828, #1844 (suites de l'EPIC #1662)
- **Vérification** : humaine — les trois règles d'écriture serveur (ne rien inventer ni effacer) portent sur la sémantique des requêtes, jugée en revue Loupe : `scripts/adr/loupe-0020-ecritures-plateforme.py`

## Contexte

VigieChiro n'est pas notre base : c'est un système **partagé**, écrit par plusieurs clients (le formulaire web, cette application, d'autres postes) et relu par tous. Trois défauts découverts coup sur coup à l'usage réel, sur le même geste (pousser les métadonnées d'un passage), ont montré que nous n'avions aucune règle explicite pour l'écriture.

**Nous inventions.** L'application représente un enregistreur inconnu par la sentinelle `INCONNU` (`recorder_id` est `NOT NULL` en base : il faut bien une valeur). La garde `idEnregistreur != null` étant **toujours vraie**, chaque dépôt publiait `detecteur_enregistreur_numserie = "INCONNU"`. La plateforme se retrouvait avec un numéro de série qui n'existe pas, que l'application **se relisait ensuite comme une donnée** — et que tout autre poste relisait pareil. Un aveu d'ignorance local était devenu un fait partagé.

**Nous effacions.** Un `PATCH` sur `configuration` **remplace le dictionnaire entier**. En n'envoyant que les clés que nous modélisons, nous supprimions silencieusement celles que le formulaire web gère et pas nous : `micro0_numero_serie`, `micro1_*`, `canal_expansion_temps`, `canal_enregistrement_direct`. L'utilisateur saisissait sa météo dans l'application et perdait, sans le savoir, la configuration matérielle qu'il avait renseignée sur le web.

**Nous parlions une langue que personne ne lisait.** Le numéro de série partait sous `detecteur_enregistreur_numserie`, alors que le formulaire web lie `detecteur_enregistreur_numero_serie`. La donnée arrivait bien sur la plateforme — et y restait **invisible**. C'est ce qui a rendu le diagnostic si long : l'envoi réussissait (`200`), le journal aurait dit « succès », et la fiche web restait vide.

Aucun de ces trois défauts ne produit d'erreur. Ils réussissent tous.

## Décision

Toute écriture vers la plateforme respecte trois règles.

**1. Ne rien inventer.** Une valeur qui signifie « je ne sais pas » ne franchit jamais la frontière. Les sentinelles locales (`INCONNU`, `PR-INCONNU`) sont reconnues par un prédicat unique, `Enregistreur.estInconnu`, et le champ correspondant est **omis** plutôt que rempli. **Ne rien dire vaut mieux que mentir** : un champ vide se corrige, une fausse donnée se propage.

**2. Ne rien effacer.** Une écriture partielle **part de l'état distant** : on relit la `configuration` de la participation, on y superpose nos clés, on renvoie l'ensemble. Nos valeurs écrasent les leurs sur les champs que nous modélisons ; tout le reste **survit**. Le corollaire vaut dans l'autre sens : ce que nous ne modélisons pas, nous n'avons pas le droit de le détruire.

**3. Parler la langue du lecteur.** La clé écrite est celle que **le consommateur lit** (ici, celle que lie le formulaire web), pas celle que nous trouvons juste. Quand une clé historique traîne, la lecture **accepte les deux** et l'écriture **retire l'ancienne** — les fiches déjà polluées se réparent alors au premier envoi.

## Conséquences

- Un champ que l'application ne connaît pas reste **vide** sur la plateforme au lieu d'y porter une sentinelle. C'est un recul apparent de complétude, et un progrès réel de véracité.
- Une écriture partielle coûte **une lecture préalable** (l'état distant). C'est le prix de la non-destruction ; il est payé une fois par envoi.
- Les participations déjà polluées par les versions antérieures (sentinelle publiée, clé historique) se **réparent au premier envoi** depuis l'application. En revanche, **aucun rattrapage en masse** n'existait à la rédaction de cet ADR : la réparation se faisait nuit par nuit, par la modale. *(Levé depuis : `metadonnees-passage --tout` rejoue la réparation sur toutes les nuits liées, #1861.)*
- Ces trois défauts ayant tous **réussi silencieusement**, ils appellent un contrôle qui ne peut pas être un code de retour : la vérification passe par les **sondes live** (`-Papi-live`) et par l'observation de la fiche web, pas par un test unitaire vert. Ce contrôle existe désormais : `AllerRetourParticipationLiveTest` (#1862) écrit, **relit**, et compare champ à champ. Chacune de ses probes fixe un **fait de plateforme** dont dépend une règle ci-dessus - si l'une tombe, c'est la règle qu'il faut rouvrir, pas la probe qu'il faut ajuster.

## Alternatives écartées

- **Écrire la sentinelle et la filtrer à la lecture.** Ne répare que *notre* lecture : la fausse donnée reste sur la plateforme pour le web et pour les autres postes.
- **Envoyer la configuration complète que nous modélisons, en assumant la perte.** C'est le comportement qui a effacé les champs du web. Un client d'un système partagé n'a pas autorité pour supprimer ce qu'il ne comprend pas.
- **Écrire les deux clés (historique et canonique).** Laisse deux sources de vérité sur la même information et repousse le problème au premier lecteur qui choisira mal.
- **Modéliser toute la configuration du formulaire web** (`micro1_*`, canaux…) pour pouvoir la réécrire sans perte. Beaucoup de surface pour des champs que l'application n'utilise pas, et la règle 2 devrait de toute façon exister pour le champ suivant que le web ajoutera.
