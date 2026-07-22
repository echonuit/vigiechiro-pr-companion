# P6 - Diagnostiquer le matériel 🩺

[← Retour au sommaire des parcours](index.md) · **Section B - Chaîne de production**

> **Persona principal** : Karim / Samuel. **Objectifs qualité visés** : aucun direct, mais c'est un usage fréquent en exploitation pro.

Karim soupçonne qu'un de ses enregistreurs a un problème (batterie qui faiblit, sonde climatique qui dérive, mises en veille intempestives). Il veut consulter les indicateurs techniques d'une nuit pour décider s'il faut remettre le matériel en service ou le réviser.

1. Karim ouvre la fiche d'un passage (vue détail).
2. Il clique sur l'onglet **« Diagnostic »**. Il y voit :
    - **Graphe de température** sur la nuit (extrait du relevé climatique)
    - **Graphe d'hygrométrie** sur la nuit
    - **Niveau de batterie** au début et à la fin de la nuit (extrait du journal du capteur)
    - **Liste des évènements anormaux** : réveils non programmés, erreurs SD, redémarrages, batterie critique
    - **Cohérence horaires** (calcul astronomique) : si les coordonnées GPS du point sont connues, l'application affiche un encart comparant la plage théorique (coucher du soleil - 30 min → lever + 30 min) à la plage effective extraite du journal du capteur. Détail [ci-dessous](#coherence-horaires-calcul-astronomique).
3. Karim **compare** avec un passage précédent du même enregistreur (par n° de série) pour repérer une dérive.
4. Il **exporte** le diagnostic en CSV ou PDF s'il veut le partager avec le fabricant ou l'archiver dans son rapport client.

## Cohérence horaires (calcul astronomique)

Le protocole Vigie-Chiro Point Fixe demande que l'enregistreur soit **allumé 30 min avant le coucher du soleil et éteint 30 min après son lever**. L'encart « Cohérence horaires » de l'onglet Diagnostic présente :

- heure de coucher de soleil **calculée localement** d'après les coordonnées GPS du point d'écoute et la date d'enregistrement
- heure de lever de soleil idem
- **plage théorique attendue** (coucher - 30 min → lever + 30 min)
- **plage effective enregistrée** (extraite du journal du capteur : heure de premier déclenchement → heure de mise en veille)
- écart : ✅ conforme / ⚠ écart de N minutes / ❌ écart majeur

Si la plage est conforme, l'utilisateur a un retour visuel rassurant. Sinon, il peut investiguer dans la liste des évènements anormaux ci-dessus.

**Précondition** : les **coordonnées GPS** doivent avoir été saisies pour le point lors de la déclaration du site ([P1](P1%20-%20Déclarer%20un%20site%20de%20suivi.md)) ou ajoutées par la suite. Sans coordonnées, l'encart est masqué et l'utilisateur est invité à compléter sa fiche site. Cette vérification est une **idée Samuel** (mai 2026) - hors MVP strict mais facile à intégrer une fois P6 en place.

## Note sur le journal du capteur

Le journal du capteur est **circulaire** sur l'enregistreur : en cas de saturation de la SD, des entrées plus anciennes peuvent avoir été effacées (R19). L'application n'a pas à reconstituer les pertes - elle exploite ce qui est présent et signale les éventuelles incohérences chronologiques.

Si la sonde climatique est absente ou défaillante, le relevé climatique est manquant et l'onglet Diagnostic le signale clairement plutôt que de masquer la section (R20).

## Enrichissements prévus

> Ces évolutions sont **décidées et maquettées, pas encore livrées**. Elles prolongent ce parcours sans en modifier les étapes actuelles.

- **L'activité horaire comme signal de dispositif.** La [courbe d'activité](../Maquettes/M-Activite.md) matérialise la fenêtre réelle entre coucher et lever du soleil, calculée au point d'écoute par la même éphéméride que la cohérence horaire de ce parcours. Des contacts qui débordent en période diurne se voient alors immédiatement : c'est un signal de matériel ou de pose autant qu'écologique (#2352).
