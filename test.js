const json = {
    id: "0x0d4a11d5EEaaC28EC3F61d100daF4d40471f1852",
    ordered: false
}

const jsonString = JSON.stringify(json);

console.log(btoa(jsonString))