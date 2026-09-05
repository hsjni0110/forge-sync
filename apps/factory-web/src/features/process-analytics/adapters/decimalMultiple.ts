// Use the JSON number's decimal representation, not binary floating-point division.
// This preserves the schema's multipleOf rule without rounding invalid input into validity.
export function isDecimalMultiple(divisor: number, value: number): boolean {
  if (!Number.isFinite(value) || !Number.isFinite(divisor) || divisor <= 0) return false;
  const fraction = (number: number) => {
    const [mantissa, exponent = "0"] = number.toString().split("e");
    const scale = (mantissa.split(".")[1]?.length ?? 0) - Number(exponent);
    const coefficient = BigInt(mantissa.replace(".", ""));
    return scale >= 0
      ? { numerator: coefficient, denominator: 10n ** BigInt(scale) }
      : { numerator: coefficient * 10n ** BigInt(-scale), denominator: 1n };
  };
  const left = fraction(value);
  const right = fraction(divisor);
  return (left.numerator * right.denominator) % (right.numerator * left.denominator) === 0n;
}
