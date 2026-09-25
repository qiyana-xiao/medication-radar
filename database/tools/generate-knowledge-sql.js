const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..', '..');
const knowledgeDir = path.join(root, 'backups', 'medication-knowledge-20260911', 'knowledge');
const output = path.join(root, 'database', 'sql', '02_knowledge_seed.sql');
const full = JSON.parse(fs.readFileSync(path.join(knowledgeDir, 'drugs-full.json'), 'utf8'));
const refined = JSON.parse(fs.readFileSync(path.join(knowledgeDir, 'medications.json'), 'utf8'));
const interactions = JSON.parse(fs.readFileSync(path.join(knowledgeDir, 'interactions.json'), 'utf8'));
const refinedByName = new Map(refined.map((item) => [item.name, item]));
const seen = new Set();

function sqlText(value) {
  const normalized = value == null ? '' : String(value);
  return `CONVERT(X'${Buffer.from(normalized, 'utf8').toString('hex')}' USING utf8mb4)`;
}

function medicationValues(item) {
  const old = refinedByName.get(item.name) || {};
  return [
    item.name,
    JSON.stringify(item.aliases || old.aliases || []),
    item.category || item.class || '',
    item.ingredients || '',
    item.appearance || '',
    item.specification || '',
    item.dosage_form || item.dosageForm || '',
    item.indications || '',
    item.usage_dosage || item.usageDosage || '',
    item.adverse_reactions || item.adverseReactions || '',
    item.contraindications || '',
    item.precautions || '',
    item.special_populations || item.specialPopulations || '',
    item.pharmacology || item.mechanism || '',
    item.therapeutic_duplication || item.therapeuticDuplication || old.therapeuticDuplication || '',
    item.monitor || old.monitor || '',
    item.notes || old.notes || '',
  ];
}

const medications = [];
for (const item of [...full, ...refined]) {
  if (!item.name || seen.has(item.name)) continue;
  seen.add(item.name);
  medications.push(item);
}

const lines = [
  '-- Generated from the backed-up medication knowledge JSON. Do not edit by hand.',
  '-- Re-run: node database/tools/generate-knowledge-sql.js',
  'USE medication_radar;',
  '',
  'START TRANSACTION;',
];

for (const item of medications) {
  lines.push(
    'INSERT INTO medications (name, aliases, category, ingredients, appearance, specification, dosage_form, indications, usage_dosage, adverse_reactions, contraindications, precautions, special_populations, pharmacology, therapeutic_duplication, monitor, notes)',
    `VALUES (${medicationValues(item).map(sqlText).join(', ')})`,
    'ON DUPLICATE KEY UPDATE name = VALUES(name);'
  );
}

for (const item of interactions) {
  lines.push(
    'INSERT INTO interactions (drug_a, drug_b, severity, mechanism, effect, recommendation)',
    `VALUES (${[item.drugA, item.drugB, item.severity, item.mechanism, item.effect, item.recommendation].map(sqlText).join(', ')})`,
    'ON DUPLICATE KEY UPDATE drug_a = VALUES(drug_a);'
  );
}

lines.push('COMMIT;', '');
fs.writeFileSync(output, lines.join('\n'), 'utf8');
console.log(`Generated ${path.relative(root, output)} with ${medications.length} medications and ${interactions.length} interactions.`);
