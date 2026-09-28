export const THEURGIES = [
  {
    id: "gravity",
    title: "Вектор падения",
    description: "На 10 секунд подчиняет цели наклону телефона.",
    price: 0,
    effectType: "tilt",
    durationSeconds: 10,
  },
  {
    id: "chrono",
    title: "Сломанные часы",
    description: "Останавливает цели на 8 секунд. Цена: таймер вылазки продолжает идти.",
    price: 10_000,
    effectType: "freeze",
    durationSeconds: 8,
  },
  {
    id: "scarlet",
    title: "Алая печать",
    description: "Улики ×2 на 12 секунд. Цена: цели ускоряются, а штрафы за промахи тоже удваиваются.",
    price: 50_000,
    effectType: "multiplier",
    durationSeconds: 12,
  },
  {
    id: "mirror",
    title: "Зеркальный договор",
    description: "Отменяет штрафы на 15 секунд. Цена: цели движутся вдвое быстрее.",
    price: 100_000,
    effectType: "shield",
    durationSeconds: 15,
  },
  {
    id: "moonfall",
    title: "Лунное затмение",
    description: "Улики ×3 на 15 секунд. Цена: цели становятся меньше, а штрафы утраиваются.",
    price: 250_000,
    effectType: "multiplier",
    durationSeconds: 15,
    multiplier: 3,
  },
  {
    id: "armageddon",
    title: "Армагедон",
    description: "После призыва уничтожает все цели на экране и умножает их цену в пять раз.",
    price: 1_000_000,
    effectType: "armageddon",
    durationSeconds: 0,
    multiplier: 5,
  },
];

export function findTheurgy(id) {
  return THEURGIES.find((item) => item.id === id);
}
