//INFO: generate test data

sz= parseInt(process.argv[2]||1000);
wantsRandom= process.argv[3];

s= new Array(sz);
s[0]='A';
for (i=1; i<sz; i++) { s[i]= wantsRandom ? Math.floor(Math.random()*10) : i%10; }
s[sz-1]='Z';

console.log(s.join(""));
