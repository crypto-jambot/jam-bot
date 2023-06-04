const json = {
    id: "0xAc4b3DacB91461209Ae9d41EC517c2B9Cb1B7DAF",
    ordered: false
}

const jsonString = JSON.stringify(json);

console.log(btoa(jsonString))