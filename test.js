const json = {
    id: "0x5777d92f208679DB4b9778590Fa3CAB3aC9e2168",
    ordered: false
}

const jsonString = JSON.stringify(json);

console.log(btoa(jsonString))