package pl.s16.countries;

public final class War {
    public final Nation a, b;
    public final long start, end;
    public boolean peaceA, peaceB;
    public War(Nation a, Nation b, long start, long end, boolean peaceA, boolean peaceB) {
        this.a=a; this.b=b; this.start=start; this.end=end; this.peaceA=peaceA; this.peaceB=peaceB;
    }
    public boolean involves(Nation n) { return n==a || n==b; }
    public boolean between(Nation x, Nation y) { return (a==x && b==y) || (a==y && b==x); }
    public boolean active() { long now=System.currentTimeMillis(); return start<=now && now<end; }
    public boolean expired() { return System.currentTimeMillis()>=end; }
}
