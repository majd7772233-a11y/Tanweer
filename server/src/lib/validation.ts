// Arabic and English character patterns
const ARABIC_NAME_PATTERN = /^[\u0621-\u064A\u0671-\u06D3]+$/;
const ENGLISH_NAME_PATTERN = /^[a-zA-Z]+$/;
const FORBIDDEN_CHARS = /[0-9\p{So}\p{Sk}\p{Sm}\p{Sc}\p{P}□♡£■€¡¤{}<>[\]_+=|\\/@#$%^&*~`!?,:;"']/u;

export function validateArabicFullName(name: string): { isValid: boolean; error?: string } {
  if (!name || typeof name !== 'string') {
    return { isValid: false, error: 'يرجى إدخال الاسم الثلاثي' };
  }

  const trimmed = name.trim().replace(/\s+/g, ' ');
  if (FORBIDDEN_CHARS.test(trimmed)) {
    return { isValid: false, error: 'الاسم يحتوي على رموز أو أرقام أو أشكال غير مسموح بها' };
  }

  const words = trimmed.split(' ').filter(w => w.length > 0);

  if (words.length < 3) {
    return { isValid: false, error: 'يجب أن يتكون الاسم من ثلاثة أجزاء على الأقل (الاسم، اسم الأب، واللقب)' };
  }

  let arabicCount = 0;
  let englishCount = 0;

  for (let i = 0; i < words.length; i++) {
    const word = words[i];
    const isArabic = ARABIC_NAME_PATTERN.test(word);
    const isEnglish = ENGLISH_NAME_PATTERN.test(word);

    if (!isArabic && !isEnglish) {
      return { isValid: false, error: `المقطع "${word}" يحتوي على حروف غير صالحة` };
    }

    if (isArabic) arabicCount++;
    if (isEnglish) englishCount++;

    if (word.length < 2) {
      return { isValid: false, error: `الاسم "${word}" قصير جداً، يجب أن يتكون كل جزء من حرفين على الأقل` };
    }

    if (/(.)\1{3,}/i.test(word)) {
      return { isValid: false, error: `المقطع "${word}" يحتوي على تكرار غير طبيعي للحروف` };
    }
  }

  if (arabicCount > 0 && englishCount > 0) {
    return { isValid: false, error: 'يرجى كتابة الاسم كاملاً بالعربية أو كاملاً بالإنجليزية دون خلط' };
  }

  return { isValid: true };
}

export function normalizePhoneNumber(phone: string): string {
  let cleaned = phone.replace(/[\s\-\(\)\+]/g, '');
  if (cleaned.startsWith('00967')) {
    cleaned = cleaned.substring(5);
  } else if (cleaned.startsWith('967')) {
    cleaned = cleaned.substring(3);
  } else if (cleaned.startsWith('07') && cleaned.length === 10) {
    cleaned = cleaned.substring(1);
  }
  return '+967' + cleaned;
}

export function validateYemenPhoneNumber(phone: string): { isValid: boolean; normalized: string; error?: string } {
  if (!phone) {
    return { isValid: false, normalized: '', error: 'يرجى إدخال رقم الهاتف' };
  }

  const normalized = normalizePhoneNumber(phone);
  // Must be in format +967 followed by 9 digits starting with 7
  const digits = normalized.replace('+967', '');
  if (!/^[0-9]+$/.test(digits)) {
    return { isValid: false, normalized, error: 'رقم الهاتف يجب أن يحتوي على أرقام فقط' };
  }

  if (digits.length !== 9) {
    return { isValid: false, normalized, error: 'رقم الهاتف اليمني يجب أن يتكون من 9 أرقام بعد رمز الدولة +967' };
  }

  if (!digits.startsWith('7')) {
    return { isValid: false, normalized, error: 'رقم الهاتف يجب أن يبدأ بالرقم 7 (مثل 77 أو 73 أو 71 أو 70 أو 78)' };
  }

  return { isValid: true, normalized };
}

export function evaluatePasswordStrength(password: string): {
  score: number; // 0 to 100
  level: 'VERY_WEAK' | 'WEAK' | 'MEDIUM' | 'STRONG' | 'VERY_STRONG';
  label: string;
  isValid: boolean;
  error?: string;
} {
  if (!password || password.length < 6) {
    return { score: 10, level: 'VERY_WEAK', label: 'ضعيفة جداً', isValid: false, error: 'كلمة المرور قصيرة جداً (6 أحرف كحد أدنى)' };
  }

  let score = 0;
  if (password.length >= 8) score += 25;
  if (password.length >= 12) score += 15;

  const hasLower = /[a-z]/.test(password) || /[\u0621-\u064A]/.test(password);
  const hasUpper = /[A-Z]/.test(password);
  const hasNumber = /[0-9]/.test(password);
  const hasSymbol = /[^a-zA-Z0-9\u0621-\u064A]/.test(password);

  if (hasLower) score += 15;
  if (hasUpper) score += 15;
  if (hasNumber) score += 15;
  if (hasSymbol) score += 15;

  // Penalty for repetitive sequences or common patterns
  const commonWeak = ['123456', '12345678', 'password', 'qwerty', '111111', 'admin123', '000000', '123123'];
  if (commonWeak.includes(password.toLowerCase())) {
    return { score: 10, level: 'VERY_WEAK', label: 'ضعيفة جداً وشائعة', isValid: false, error: 'كلمة المرور شائعة جداً وسهلة التخمين' };
  }

  if (/(.)\1{3,}/.test(password)) {
    score -= 20;
  }

  score = Math.max(10, Math.min(100, score));

  if (score < 30) {
    return { score, level: 'VERY_WEAK', label: 'ضعيفة جداً', isValid: false, error: 'كلمة المرور ضعيفة جداً، استخدم تشكيلة من الأحرف والأرقام والرموز' };
  } else if (score < 50) {
    return { score, level: 'WEAK', label: 'ضعيفة', isValid: true };
  } else if (score < 75) {
    return { score, level: 'MEDIUM', label: 'متوسطة', isValid: true };
  } else if (score < 90) {
    return { score, level: 'STRONG', label: 'قوية', isValid: true };
  } else {
    return { score, level: 'VERY_STRONG', label: 'ممتازة وأسطورية 🛡️', isValid: true };
  }
}

export function isValidGradeSection(gradeId: number, sectionId: string): boolean {
  if (gradeId === 7) return ['A'].includes(sectionId);
  if (gradeId === 8 || gradeId === 9) return ['A', 'B'].includes(sectionId);
  if (gradeId === 10 || gradeId === 11) return ['A', 'B', 'C', 'D'].includes(sectionId);
  if (gradeId === 12) return ['A', 'B', 'C'].includes(sectionId);
  return false;
}
